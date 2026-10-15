package com.example.faturasolarapp   // <-- mantenha igual ao package do seu projeto

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ------------------------------------------------------------------ Formatação
private fun BigDecimal.kwh(): String =
    stripTrailingZeros().toPlainString().replace('.', ',')

private fun BigDecimal.reais(): String =
    setScale(2, RoundingMode.HALF_UP).toPlainString().replace('.', ',')

private fun BigDecimal.tarifa(): String =
    setScale(6, RoundingMode.HALF_UP).toPlainString().replace('.', ',')

/** O que vai em uma página de detalhes do relatório. */
data class ItemRelatorio(val titulo: String, val campos: Campos, val r: ResultadoFatura)

// ------------------------------------------------------------------ Ações (compartilhar / imprimir)

/** Executa a ação e, se der erro, mostra uma mensagem em vez de fechar o app. */
fun executarSeguro(contexto: Context, acao: () -> Unit) {
    try {
        acao()
    } catch (e: Exception) {
        Toast.makeText(contexto, "Não foi possível gerar o PDF: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

/** Abre o menu "Compartilhar" do Android (Bluetooth, WhatsApp, e-mail, Drive...). */
fun compartilharPdf(contexto: Context, arquivo: File, assunto: String) {
    val uri = FileProvider.getUriForFile(contexto, "${contexto.packageName}.fileprovider", arquivo)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, assunto)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = Intent.createChooser(intent, "Compartilhar PDF (Bluetooth, WhatsApp, e-mail...)")
    if (contexto !is Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    contexto.startActivity(chooser)
}

/** Abre a tela de impressão do Android com o PDF. */
fun imprimirPdf(contexto: Context, arquivo: File, nome: String) {
    val activity = contexto.achaActivity() ?: return
    val gerenciador = activity.getSystemService(Context.PRINT_SERVICE) as PrintManager
    gerenciador.print(nome, PdfPrintAdapter(arquivo, nome), null)
}

private fun Context.achaActivity(): Activity? {
    var c: Context = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

private class PdfPrintAdapter(
    private val arquivo: File,
    private val nome: String
) : PrintDocumentAdapter() {

    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes?,
        cancellationSignal: CancellationSignal?,
        callback: LayoutResultCallback?,
        extras: Bundle?
    ) {
        if (cancellationSignal?.isCanceled == true) {
            callback?.onLayoutCancelled()
            return
        }
        val info = PrintDocumentInfo.Builder(nome)
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
            .setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN)
            .build()
        callback?.onLayoutFinished(info, true)
    }

    override fun onWrite(
        pages: Array<out PageRange>?,
        destination: ParcelFileDescriptor?,
        cancellationSignal: CancellationSignal?,
        callback: WriteResultCallback?
    ) {
        try {
            FileInputStream(arquivo).use { entrada ->
                FileOutputStream(destination!!.fileDescriptor).use { saida ->
                    entrada.copyTo(saida)
                }
            }
            callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
        } catch (e: Exception) {
            callback?.onWriteFailed(e.message)
        }
    }
}

// ------------------------------------------------------------------ Geração do PDF
object RelatorioPdf {

    private const val LARG = 595        // A4 em pontos
    private const val ALT = 842
    private const val MARGEM = 40f

    private val AZUL = Color.parseColor("#4FAEF5")
    private val ESCURO = Color.parseColor("#1A2B3C")
    private val CINZA = Color.parseColor("#6B7B8B")
    private val AMARELO_CLARO = Color.parseColor("#FFF3C4")

    /** PDF de um único resultado (1 página). */
    fun gerarMes(contexto: Context, item: ItemRelatorio): File {
        val doc = PdfDocument()
        desenharMes(contexto, doc, 1, item)
        return gravar(contexto, doc, "fatura_${nomeSeguro(item.titulo)}.pdf")
    }

    /** PDF do ano: 1 página de resumo + 1 página de detalhes por mês salvo. Retorna null se não houver nada salvo. */
    fun gerarAno(contexto: Context, ano: Int, registros: List<Registro>): File? {
        val dados = registros
            .filter { it.ano == ano }
            .sortedBy { it.mes }
            .mapNotNull { reg ->
                runCatching { calcularFatura(reg.campos.paraEntrada()) }.getOrNull()
                    ?.let { resultado -> Pair(reg, resultado) }
            }
        if (dados.isEmpty()) return null

        val doc = PdfDocument()
        desenharResumoAno(contexto, doc, 1, ano, dados)
        dados.forEachIndexed { i, par ->
            val reg = par.first
            desenharMes(
                contexto, doc, i + 2,
                ItemRelatorio("${MESES[reg.mes - 1]}/${reg.ano}", reg.campos, par.second)
            )
        }
        return gravar(contexto, doc, "relatorio_anual_$ano.pdf")
    }

    // ---------------------------------------------------------- Páginas
    private fun desenharMes(contexto: Context, doc: PdfDocument, numero: Int, item: ItemRelatorio) {
        val pagina = novaPagina(doc, numero)
        val c = pagina.canvas
        val campos = item.campos
        val r = item.r

        var y = cabecalho(contexto, c, "Resultado: ${item.titulo}")

        y = secao(c, y, "Dados informados")
        y = linha(c, y, "Energia ativa (consumo da rede)", "de ${campos.ativaAnt} a ${campos.ativaAtual} kWh")
        y = linha(c, y, "Energia reversa (injetada)", "de ${campos.revAnt} a ${campos.revAtual} kWh")
        y = linha(c, y, "Reserva acumulada anterior", "${campos.saldoAnt} kWh")
        y = linha(c, y, "Tarifa base", "R$ ${campos.tarifaBase}/kWh")
        y = linha(c, y, "Bandeira", "R$ ${campos.bandeira}/kWh")
        y = linha(c, y, "COSIP", "R$ ${campos.cosip}")
        y = linha(
            c, y, "Tipo de ligação",
            "${campos.ligacao.rotulo} (${campos.ligacao.disponibilidade} kWh)"
        )

        y += 12f
        y = secao(c, y, "Energia (kWh)")
        y = linha(c, y, "Consumo medido (ativa)", "${r.consumo.kwh()} kWh")
        y = linha(c, y, "Energia injetada no mês (reversa)", "${r.injetada.kwh()} kWh")
        y = linha(c, y, "Custo de disponibilidade", "${r.disponibilidade.kwh()} kWh")
        y = linha(c, y, "kWh a compensar", "${r.aCompensar.kwh()} kWh")
        y = linha(c, y, "   crédito do mês", "${r.creditoMes.kwh()} kWh")
        y = linha(c, y, "   Resíduo En Reversa (do saldo)", "${r.residuo.kwh()} kWh")
        y = linha(c, y, "Saldo acumulado após compensação", "${r.saldoFinal.kwh()} kWh")

        y += 12f
        y = secao(c, y, "Valores da fatura (R$)")
        y = linha(
            c, y,
            "Consumo ${r.kwhCompensado.kwh()} kWh a ${r.tarifaBase.tarifa()}",
            "R$ ${r.itemConsumoCompensado.reais()}"
        )
        y = linha(
            c, y,
            "Consumo ${r.kwhCobrado.kwh()} kWh a ${r.tarifaComBandeira.tarifa()}",
            "R$ ${r.itemConsumoCobrado.reais()}"
        )
        y = linha(c, y, "COSIP", "R$ ${r.cosip.reais()}")
        y = linha(c, y, "Crédito de Geração F/Ponta", "R$ ${r.creditoGeracao.reais()}")

        // Caixa do total
        y += 8f
        c.drawRoundRect(RectF(MARGEM, y, LARG - MARGEM, y + 46f), 8f, 8f, preenchimento(AMARELO_CLARO))
        c.drawText("TOTAL A PAGAR", MARGEM + 12f, y + 29f, tinta(14f, true))
        val total = "R$ ${r.total.reais()}"
        val pTotal = tinta(19f, true)
        c.drawText(total, LARG - MARGEM - 12f - pTotal.measureText(total), y + 30f, pTotal)
        y += 66f

        c.drawText(
            "Adicional de bandeira (R$ ${r.adicionalBandeira.reais()}) é informativo e já está incluso no consumo cobrado.",
            MARGEM, y, tinta(9f, false, CINZA)
        )

        rodape(c, numero)
        doc.finishPage(pagina)
    }

    private fun desenharResumoAno(
        contexto: Context,
        doc: PdfDocument,
        numero: Int,
        ano: Int,
        dados: List<Pair<Registro, ResultadoFatura>>
    ) {
        val pagina = novaPagina(doc, numero)
        val c = pagina.canvas

        var y = cabecalho(contexto, c, "Relatório anual $ano")
        y = secao(c, y, "Resumo dos 12 meses")

        // colunas alinhadas à direita
        val xConsumo = 250f
        val xInjetada = 340f
        val xSaldo = 440f
        val xTotal = LARG - MARGEM

        val pTitulo = tinta(10f, true, CINZA)
        c.drawText("Mês", MARGEM, y, pTitulo)
        textoDireita(c, "Consumo kWh", xConsumo, y, pTitulo)
        textoDireita(c, "Injetada kWh", xInjetada, y, pTitulo)
        textoDireita(c, "Saldo kWh", xSaldo, y, pTitulo)
        textoDireita(c, "Total R$", xTotal, y, pTitulo)
        y += 8f
        c.drawLine(MARGEM, y, LARG - MARGEM, y, traco())
        y += 16f

        for (mes in 1..12) {
            val par = dados.firstOrNull { it.first.mes == mes }
            c.drawText(MESES[mes - 1], MARGEM, y, tinta(11f))
            if (par != null) {
                val r = par.second
                val p = tinta(11f)
                textoDireita(c, r.consumo.kwh(), xConsumo, y, p)
                textoDireita(c, r.injetada.kwh(), xInjetada, y, p)
                textoDireita(c, r.saldoFinal.kwh(), xSaldo, y, p)
                textoDireita(c, r.total.reais(), xTotal, y, tinta(11f, true))
            } else {
                textoDireita(c, "sem registro", xTotal, y, tinta(10f, false, CINZA))
            }
            y += 20f
        }

        y += 4f
        c.drawLine(MARGEM, y, LARG - MARGEM, y, traco())
        y += 22f

        val soma = dados.fold(BigDecimal.ZERO) { acc, par -> acc + par.second.total }
        c.drawText("Meses salvos: ${dados.size} de 12", MARGEM, y, tinta(11f))
        y += 22f
        c.drawRoundRect(RectF(MARGEM, y, LARG - MARGEM, y + 46f), 8f, 8f, preenchimento(AMARELO_CLARO))
        c.drawText("TOTAL PAGO NOS MESES SALVOS", MARGEM + 12f, y + 29f, tinta(13f, true))
        val txt = "R$ ${soma.reais()}"
        val pSoma = tinta(19f, true)
        c.drawText(txt, LARG - MARGEM - 12f - pSoma.measureText(txt), y + 30f, pSoma)

        rodape(c, numero)
        doc.finishPage(pagina)
    }

    // ---------------------------------------------------------- Peças de desenho
    private fun novaPagina(doc: PdfDocument, numero: Int): PdfDocument.Page =
        doc.startPage(PdfDocument.PageInfo.Builder(LARG, ALT, numero).create())

    private fun cabecalho(contexto: Context, c: Canvas, titulo: String): Float {
        c.drawRect(0f, 0f, LARG.toFloat(), 90f, preenchimento(AZUL))
        c.drawText("Fatura Solar App", MARGEM, 42f, tinta(22f, true, Color.WHITE))
        c.drawText(titulo, MARGEM, 66f, tinta(15f, false, Color.WHITE))

        val logo = BitmapFactory.decodeResource(contexto.resources, R.drawable.logo_solar)
        if (logo != null) {
            val largura = 110f
            val altura = largura * logo.height / logo.width
            val topo = (90f - altura) / 2f
            c.drawBitmap(
                logo, null,
                RectF(LARG - MARGEM - largura, topo, LARG - MARGEM, topo + altura),
                null
            )
            logo.recycle()
        }
        return 90f + 30f
    }

    private fun rodape(c: Canvas, numero: Int) {
        val data = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR")).format(Date())
        c.drawLine(MARGEM, ALT - 40f, LARG - MARGEM, ALT - 40f, traco())
        c.drawText(
            "Gerado em $data  •  Fatura Solar App - By Nadson Cley B. de Araújo",
            MARGEM, ALT - 24f, tinta(9f, false, CINZA)
        )
        textoDireita(c, "Página $numero", LARG - MARGEM, ALT - 24f, tinta(9f, false, CINZA))
    }

    private fun secao(c: Canvas, y: Float, titulo: String): Float {
        c.drawText(titulo, MARGEM, y, tinta(13f, true))
        c.drawLine(MARGEM, y + 5f, LARG - MARGEM, y + 5f, traco())
        return y + 24f
    }

    private fun linha(c: Canvas, y: Float, rotulo: String, valor: String): Float {
        c.drawText(rotulo, MARGEM, y, tinta(11.5f))
        textoDireita(c, valor, LARG - MARGEM, y, tinta(11.5f, true))
        return y + 18f
    }

    private fun textoDireita(c: Canvas, texto: String, xDireita: Float, y: Float, paint: Paint) {
        c.drawText(texto, xDireita - paint.measureText(texto), y, paint)
    }

    private fun tinta(tamanho: Float, negrito: Boolean = false, cor: Int = ESCURO): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = tamanho
            color = cor
            typeface = if (negrito) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }

    private fun preenchimento(cor: Int): Paint =
        Paint().apply {
            color = cor
            style = Paint.Style.FILL
        }

    private fun traco(): Paint =
        Paint().apply {
            color = Color.LTGRAY
            strokeWidth = 1f
        }

    // ---------------------------------------------------------- Arquivo
    private fun gravar(contexto: Context, doc: PdfDocument, nome: String): File {
        val pasta = File(contexto.cacheDir, "pdfs").apply { mkdirs() }
        val arquivo = File(pasta, nome)
        FileOutputStream(arquivo).use { doc.writeTo(it) }
        doc.close()
        return arquivo
    }

    private fun nomeSeguro(texto: String): String =
        Normalizer.normalize(texto, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
}