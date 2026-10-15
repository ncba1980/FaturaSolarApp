package com.example.faturasolarapp   // <-- mantenha igual ao package do seu projeto

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.math.BigDecimal
import java.math.RoundingMode

// ------------------------------------------------------------------ Activity
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                FaturaSolarApp()
            }
        }
    }
}

// ------------------------------------------------------------------ Constantes visuais
private val AzulTopo = Color(0xFF4FAEF5)
private val AzulBase = Color(0xFFB5DCF8)
private val Amarelo = Color(0xFFFFC107)
private val TextoEscuro = Color(0xFF1A2B3C)
private val TextoRotulo = Color(0xFF5A6B7B)
private val Vermelho = Color(0xFFB00020)

/** Proporção largura/altura da imagem do logo (547 x 365). */
private const val LOGO_PROPORCAO = 1.4986f

// ------------------------------------------------------------------ Navegação
enum class Tela { INICIAL, DADOS, RESULTADO, HISTORICO }

private val CamposSaver = listSaver<Campos, Any>(
    save = {
        listOf(
            it.ativaAnt, it.ativaAtual, it.revAnt, it.revAtual,
            it.saldoAnt, it.tarifaBase, it.bandeira, it.cosip, it.ligacao.name
        )
    },
    restore = {
        Campos(
            ativaAnt = it[0] as String,
            ativaAtual = it[1] as String,
            revAnt = it[2] as String,
            revAtual = it[3] as String,
            saldoAnt = it[4] as String,
            tarifaBase = it[5] as String,
            bandeira = it[6] as String,
            cosip = it[7] as String,
            ligacao = Ligacao.valueOf(it[8] as String)
        )
    }
)

@Composable
fun FaturaSolarApp() {
    val contexto = LocalContext.current
    val repo = remember { RepositorioHistorico(contexto) }
    var registros by remember { mutableStateOf(repo.todos()) }

    var tela by rememberSaveable { mutableStateOf(Tela.INICIAL) }
    var campos by rememberSaveable(stateSaver = CamposSaver) { mutableStateOf(Campos()) }
    var perguntarSalvar by rememberSaveable { mutableStateOf(false) }
    var abertoAno by rememberSaveable { mutableIntStateOf(0) }   // 0 = não está abrindo um salvo
    var abertoMes by rememberSaveable { mutableIntStateOf(0) }
    var anoHistorico by rememberSaveable { mutableIntStateOf(anoAtual()) }

    // Botão "voltar" do Android
    BackHandler(enabled = tela == Tela.RESULTADO || tela == Tela.HISTORICO) {
        tela = if (tela == Tela.RESULTADO && abertoAno != 0) Tela.HISTORICO else Tela.DADOS
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(AzulTopo, AzulBase)))
            .systemBarsPadding()
    ) {
        when (tela) {
            Tela.INICIAL -> TelaInicial(onContinuar = { tela = Tela.DADOS })

            Tela.DADOS -> TelaDados(
                campos = campos,
                onChange = { campos = it },
                onCalcular = {
                    abertoAno = 0
                    abertoMes = 0
                    perguntarSalvar = true      // pergunta se quer salvar
                    tela = Tela.RESULTADO
                },
                onHistorico = { tela = Tela.HISTORICO }
            )

            Tela.RESULTADO -> {
                val registroAberto =
                    if (abertoAno != 0) registros.firstOrNull { it.ano == abertoAno && it.mes == abertoMes }
                    else null
                val camposUsados = registroAberto?.campos ?: campos
                val resultado = remember(camposUsados) {
                    runCatching { calcularFatura(camposUsados.paraEntrada()) }.getOrNull()
                }

                if (resultado == null) {
                    tela = Tela.DADOS
                } else {
                    val tituloPdf =
                        if (registroAberto != null) "${MESES[registroAberto.mes - 1]}/${registroAberto.ano}"
                        else "Resultado da fatura"
                    val gerarPdf = {
                        RelatorioPdf.gerarMes(contexto, ItemRelatorio(tituloPdf, camposUsados, resultado))
                    }

                    TelaResultado(
                        r = resultado,
                        titulo = if (registroAberto != null) "Resultado de $tituloPdf" else tituloPdf,
                        salvo = registroAberto != null,
                        onSalvar = { perguntarSalvar = true },
                        onPdf = {
                            executarSeguro(contexto) {
                                compartilharPdf(contexto, gerarPdf(), "Fatura Solar - $tituloPdf")
                            }
                        },
                        onImprimir = {
                            executarSeguro(contexto) {
                                imprimirPdf(contexto, gerarPdf(), "Fatura Solar - $tituloPdf")
                            }
                        },
                        onVoltar = { tela = if (registroAberto != null) Tela.HISTORICO else Tela.DADOS }
                    )

                    if (perguntarSalvar && registroAberto == null) {
                        DialogSalvar(
                            registros = registros,
                            onSalvar = { mes, ano ->
                                repo.salvar(Registro(ano, mes, campos))
                                registros = repo.todos()
                                anoHistorico = ano
                                perguntarSalvar = false
                                Toast.makeText(
                                    contexto,
                                    "Resultado salvo em ${MESES[mes - 1]}/$ano",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            onNaoSalvar = { perguntarSalvar = false }
                        )
                    }
                }
            }

            Tela.HISTORICO -> TelaHistorico(
                registros = registros,
                ano = anoHistorico,
                onAno = { anoHistorico = it },
                onAbrir = { reg ->
                    abertoAno = reg.ano
                    abertoMes = reg.mes
                    tela = Tela.RESULTADO
                },
                onExcluir = { reg ->
                    repo.excluir(reg.ano, reg.mes)
                    registros = repo.todos()
                },
                onPdfAno = {
                    executarSeguro(contexto) {
                        RelatorioPdf.gerarAno(contexto, anoHistorico, registros)?.let {
                            compartilharPdf(contexto, it, "Fatura Solar - Relatório $anoHistorico")
                        }
                    }
                },
                onImprimirAno = {
                    executarSeguro(contexto) {
                        RelatorioPdf.gerarAno(contexto, anoHistorico, registros)?.let {
                            imprimirPdf(contexto, it, "Fatura Solar - Relatório $anoHistorico")
                        }
                    }
                },
                onVoltar = { tela = Tela.DADOS }
            )
        }
    }
}

// ------------------------------------------------------------------ Blocos reutilizáveis
/**
 * Página rolável e centralizada. Em tablets o conteúdo fica limitado a 1000 dp de largura.
 * [conteudo] recebe a largura disponível para decidir quantas colunas usar.
 */
@Composable
private fun Pagina(conteudo: @Composable (Dp) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val largura = maxWidth
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .widthIn(max = 1000.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            conteudo(largura)
        }
    }
}

/** Distribui [quantidade] itens em [colunas] colunas. */
@Composable
private fun Grade(colunas: Int, quantidade: Int, item: @Composable (Int) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        (0 until quantidade).chunked(colunas).forEach { linha ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                linha.forEach { i ->
                    Box(Modifier.weight(1f)) { item(i) }
                }
                repeat(colunas - linha.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** Logo: a imagem que você enviou (res/drawable-nodpi/logo_solar.png). */
@Composable
fun Logo(largura: Dp) {
    Image(
        painter = painterResource(R.drawable.logo_solar),
        contentDescription = "Logo Fatura Solar App",
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .width(largura)
            .aspectRatio(LOGO_PROPORCAO)
            .clip(RoundedCornerShape(largura * 0.06f))
    )
}

@Composable
private fun BotaoAmarelo(
    texto: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tamanhoFonte: TextUnit = 15.sp,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = Amarelo,
            contentColor = TextoEscuro,
            disabledContainerColor = Color(0xFFE3E3E3),
            disabledContentColor = Color.Gray
        ),
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = 6.dp),
        modifier = modifier.height(44.dp)
    ) {
        Text(texto, fontSize = tamanhoFonte, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

// ------------------------------------------------------------------ Tela 1: inicial
@Composable
fun TelaInicial(onContinuar: () -> Unit) {
    // avança sozinho depois de 2,5 s; também avança ao tocar na tela
    LaunchedEffect(Unit) {
        delay(2500)
        onContinuar()
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .clickable { onContinuar() }
    ) {
        // o logo se ajusta à largura E à altura da tela (celular, tablet, retrato ou paisagem)
        val larguraLogo = minOf(maxWidth * 0.8f, 480.dp, maxHeight * 0.5f * LOGO_PROPORCAO)
        val tamanhoTitulo = if (maxWidth >= 600.dp) 44.sp else 32.sp

        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Logo(larguraLogo)
            Spacer(Modifier.height(16.dp))
            Text(
                "Fatura Solar App",
                color = Color.White,
                fontSize = tamanhoTitulo,
                fontWeight = FontWeight.Bold
            )
        }
        Text(
            "By Nadson Cley B. de Araújo",
            color = TextoEscuro,
            fontSize = if (maxWidth >= 600.dp) 20.sp else 16.sp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
        )
    }
}

// ------------------------------------------------------------------ Tela 2: dados
@Composable
fun TelaDados(
    campos: Campos,
    onChange: (Campos) -> Unit,
    onCalcular: () -> Unit,
    onHistorico: () -> Unit
) {
    var erro by rememberSaveable { mutableStateOf<String?>(null) }

    val acaoCalcular: () -> Unit = {
        runCatching { campos.paraEntrada() }
            .onSuccess {
                erro = null
                onCalcular()
            }
            .onFailure { erro = it.message }
    }
    val acaoLimpar: () -> Unit = {
        onChange(Campos())
        erro = null
    }

    Pagina { larg ->
        val largo = larg >= 600.dp

        Text(
            "Fatura Solar App",
            color = Color.White,
            fontSize = if (largo) 28.sp else 22.sp,
            fontWeight = FontWeight.Bold
        )

        // 2 colunas no celular, 4 em tablet
        Grade(colunas = if (largo) 4 else 2, quantidade = 8) { i ->
            when (i) {
                0 -> CampoCompacto("Ativa anterior (kWh)", campos.ativaAnt) {
                    onChange(campos.copy(ativaAnt = it))
                }
                1 -> CampoCompacto("Ativa atual (kWh)", campos.ativaAtual) {
                    onChange(campos.copy(ativaAtual = it))
                }
                2 -> CampoCompacto("Reversa anterior (kWh)", campos.revAnt) {
                    onChange(campos.copy(revAnt = it))
                }
                3 -> CampoCompacto("Reversa atual (kWh)", campos.revAtual) {
                    onChange(campos.copy(revAtual = it))
                }
                4 -> CampoCompacto("Reserva anterior (kWh)", campos.saldoAnt) {
                    onChange(campos.copy(saldoAnt = it))
                }
                5 -> CampoCompacto("Tarifa base (R$/kWh)", campos.tarifaBase, "0,875710") {
                    onChange(campos.copy(tarifaBase = it))
                }
                6 -> CampoCompacto("Bandeira (R$/kWh)", campos.bandeira, "0,01885") {
                    onChange(campos.copy(bandeira = it))
                }
                else -> CampoCompacto("COSIP (R$)", campos.cosip, "53,33") {
                    onChange(campos.copy(cosip = it))
                }
            }
        }

        // Tipo de ligação (botões compactos)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("Tipo de ligação (custo de disponibilidade)", fontSize = 11.sp, color = TextoRotulo)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Ligacao.values().forEach { opcao ->
                    val selecionado = campos.ligacao == opcao
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selecionado) Amarelo else Color(0xFFEAF1F7))
                            .clickable { onChange(campos.copy(ligacao = opcao)) }
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "${opcao.rotulo}\n${opcao.disponibilidade} kWh",
                            fontSize = 12.sp,
                            fontWeight = if (selecionado) FontWeight.Bold else FontWeight.Normal,
                            color = TextoEscuro,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        // Botões na mesma linha
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BotaoAmarelo("Calcular", Modifier.weight(1f), onClick = acaoCalcular)
            BotaoAmarelo("Limpar", Modifier.weight(1f), onClick = acaoLimpar)
            BotaoAmarelo("Histórico", Modifier.weight(1f), onClick = onHistorico)
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFDF5))
        ) {
            Text(
                text = erro ?: "O resultado aparecerá na próxima tela",
                color = if (erro != null) Vermelho else TextoEscuro,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp)
            )
        }
    }
}

/** Campo de texto compacto: rótulo pequeno em cima e valor embaixo. */
@Composable
private fun CampoCompacto(
    rotulo: String,
    valor: String,
    dica: String = "",
    onChange: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .padding(horizontal = 12.dp, vertical = 5.dp)
    ) {
        Text(rotulo, fontSize = 11.sp, color = TextoRotulo, maxLines = 1)
        BasicTextField(
            value = valor,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(fontSize = 15.sp, color = TextoEscuro),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            cursorBrush = SolidColor(TextoEscuro),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { campoInterno ->
                Box {
                    if (valor.isEmpty() && dica.isNotEmpty()) {
                        Text(dica, fontSize = 15.sp, color = Color(0xFFB0BAC4))
                    }
                    campoInterno()
                }
            }
        )
    }
}

// ------------------------------------------------------------------ Formatação
private fun BigDecimal.kwh(): String =
    stripTrailingZeros().toPlainString().replace('.', ',')

private fun BigDecimal.reais(): String =
    setScale(2, RoundingMode.HALF_UP).toPlainString().replace('.', ',')

private fun BigDecimal.tarifa(): String =
    setScale(6, RoundingMode.HALF_UP).toPlainString().replace('.', ',')

// ------------------------------------------------------------------ Tela 3: resultado
@Composable
fun TelaResultado(
    r: ResultadoFatura,
    titulo: String,
    salvo: Boolean,               // true = está abrindo um resultado já salvo
    onSalvar: () -> Unit,
    onPdf: () -> Unit,
    onImprimir: () -> Unit,
    onVoltar: () -> Unit
) {
    Pagina { larg ->
        val largo = larg >= 600.dp

        Text(
            titulo,
            color = Color.White,
            fontSize = if (largo) 26.sp else 20.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        val energia: @Composable () -> Unit = {
            Secao("Energia (kWh)") {
                Linha("Consumo medido (ativa)", "${r.consumo.kwh()} kWh")
                Linha("Energia injetada (reversa)", "${r.injetada.kwh()} kWh")
                Linha("Custo de disponibilidade", "${r.disponibilidade.kwh()} kWh")
                Linha("kWh a compensar", "${r.aCompensar.kwh()} kWh")
                Linha("   crédito do mês", "${r.creditoMes.kwh()} kWh")
                Linha("   Resíduo En Reversa (saldo)", "${r.residuo.kwh()} kWh")
                Linha("Saldo acumulado após", "${r.saldoFinal.kwh()} kWh")
            }
        }

        val valores: @Composable () -> Unit = {
            Secao("Valores da fatura (R$)") {
                Linha(
                    "Consumo ${r.kwhCompensado.kwh()} kWh a ${r.tarifaBase.tarifa()}",
                    "R$ ${r.itemConsumoCompensado.reais()}"
                )
                Linha(
                    "Consumo ${r.kwhCobrado.kwh()} kWh a ${r.tarifaComBandeira.tarifa()}",
                    "R$ ${r.itemConsumoCobrado.reais()}"
                )
                Linha("COSIP", "R$ ${r.cosip.reais()}")
                Linha("Crédito de Geração F/Ponta", "R$ ${r.creditoGeracao.reais()}")
                HorizontalDivider(Modifier.padding(vertical = 2.dp))
                Linha(
                    "Adicional Bandeira (informativo, já incluso)",
                    "R$ ${r.adicionalBandeira.reais()}"
                )
                Text(
                    "Cálculo teórico da bandeira: " +
                            r.adicionalBandeiraTeorico.setScale(4, RoundingMode.HALF_UP)
                                .toPlainString().replace('.', ','),
                    fontSize = 11.sp,
                    color = Color.Gray
                )
            }
        }

        if (largo) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) { energia() }
                Box(Modifier.weight(1f)) { valores() }
            }
        } else {
            energia()
            valores()
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3C4))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("TOTAL A PAGAR", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextoEscuro)
                Text("R$ ${r.total.reais()}", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = TextoEscuro)
            }
        }

        if (salvo) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BotaoAmarelo("PDF", Modifier.weight(1f), onClick = onPdf)
                BotaoAmarelo("Imprimir", Modifier.weight(1f), onClick = onImprimir)
                BotaoAmarelo("Voltar", Modifier.weight(1f), onClick = onVoltar)
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BotaoAmarelo("Salvar", Modifier.weight(1f), tamanhoFonte = 13.sp, onClick = onSalvar)
                BotaoAmarelo("PDF", Modifier.weight(1f), tamanhoFonte = 13.sp, onClick = onPdf)
                BotaoAmarelo("Imprimir", Modifier.weight(1f), tamanhoFonte = 13.sp, onClick = onImprimir)
                BotaoAmarelo("Voltar", Modifier.weight(1f), tamanhoFonte = 13.sp, onClick = onVoltar)
            }
        }
    }
}

@Composable
private fun Secao(titulo: String, conteudo: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(titulo, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextoEscuro)
            HorizontalDivider()
            conteudo()
        }
    }
}

@Composable
private fun Linha(rotulo: String, valor: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(rotulo, modifier = Modifier.weight(1f), fontSize = 13.sp, color = TextoEscuro)
        Spacer(Modifier.size(8.dp))
        Text(valor, fontWeight = FontWeight.Medium, fontSize = 13.sp, color = TextoEscuro)
    }
}

// ------------------------------------------------------------------ Pergunta: salvar?
@Composable
private fun DialogSalvar(
    registros: List<Registro>,
    onSalvar: (mes: Int, ano: Int) -> Unit,
    onNaoSalvar: () -> Unit
) {
    var mes by rememberSaveable { mutableIntStateOf(mesAtual()) }
    var ano by rememberSaveable { mutableIntStateOf(anoAtual()) }
    var menuAberto by remember { mutableStateOf(false) }
    val jaExiste = registros.any { it.ano == ano && it.mes == mes }

    AlertDialog(
        onDismissRequest = onNaoSalvar,
        title = { Text("Salvar resultado?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Deseja salvar este resultado no histórico? Escolha o mês de referência da fatura:")

                Box {
                    OutlinedButton(onClick = { menuAberto = true }) {
                        Text("Mês: ${MESES[mes - 1]}")
                    }
                    DropdownMenu(expanded = menuAberto, onDismissRequest = { menuAberto = false }) {
                        MESES.forEachIndexed { i, nome ->
                            DropdownMenuItem(
                                text = { Text(nome) },
                                onClick = {
                                    mes = i + 1
                                    menuAberto = false
                                }
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { if (ano > 2000) ano-- }) { Text("−") }
                    Text(
                        "Ano: $ano",
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    OutlinedButton(onClick = { if (ano < 2100) ano++ }) { Text("+") }
                }

                if (jaExiste) {
                    Text(
                        "Já existe um resultado de ${MESES[mes - 1]}/$ano. Ele será substituído.",
                        color = Vermelho,
                        fontSize = 13.sp
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSalvar(mes, ano) }) { Text("Salvar") }
        },
        dismissButton = {
            TextButton(onClick = onNaoSalvar) { Text("Não salvar") }
        }
    )
}

// ------------------------------------------------------------------ Tela 4: histórico (12 meses)
@Composable
fun TelaHistorico(
    registros: List<Registro>,
    ano: Int,
    onAno: (Int) -> Unit,
    onAbrir: (Registro) -> Unit,
    onExcluir: (Registro) -> Unit,
    onPdfAno: () -> Unit,
    onImprimirAno: () -> Unit,
    onVoltar: () -> Unit
) {
    var paraExcluir by remember { mutableStateOf<Registro?>(null) }

    Pagina { larg ->
        // 1 coluna no celular, 2 em tablet pequeno, 3 em tablet grande
        val colunas = when {
            larg >= 840.dp -> 3
            larg >= 600.dp -> 2
            else -> 1
        }

        Logo(if (larg >= 600.dp) 180.dp else 120.dp)
        Text(
            "Histórico de resultados",
            color = Color.White,
            fontSize = if (larg >= 600.dp) 30.sp else 24.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            BotaoAmarelo("◀", Modifier.width(72.dp)) { if (ano > 2000) onAno(ano - 1) }
            Text("$ano", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            BotaoAmarelo("▶", Modifier.width(72.dp)) { if (ano < 2100) onAno(ano + 1) }
        }

        val salvos = registros.count { it.ano == ano }
        Text("Meses salvos em $ano: $salvos de 12", color = TextoEscuro, fontSize = 14.sp)

        // Relatório do ano inteiro: compartilhar (Bluetooth etc.) ou imprimir
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BotaoAmarelo("PDF do ano", Modifier.weight(1f), enabled = salvos > 0, onClick = onPdfAno)
            BotaoAmarelo("Imprimir ano", Modifier.weight(1f), enabled = salvos > 0, onClick = onImprimirAno)
        }

        Grade(colunas = colunas, quantidade = 12) { i ->
            val registro = registros.firstOrNull { it.ano == ano && it.mes == i + 1 }
            CartaoMes(
                mes = i + 1,
                registro = registro,
                onAbrir = { registro?.let(onAbrir) },
                onExcluir = { paraExcluir = registro }
            )
        }

        BotaoAmarelo("Voltar", Modifier.fillMaxWidth(), onClick = onVoltar)
    }

    paraExcluir?.let { reg ->
        AlertDialog(
            onDismissRequest = { paraExcluir = null },
            title = { Text("Excluir resultado?") },
            text = { Text("Excluir o resultado de ${MESES[reg.mes - 1]}/${reg.ano}? Essa ação não pode ser desfeita.") },
            confirmButton = {
                TextButton(onClick = {
                    onExcluir(reg)
                    paraExcluir = null
                }) { Text("Excluir", color = Vermelho) }
            },
            dismissButton = {
                TextButton(onClick = { paraExcluir = null }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
private fun CartaoMes(
    mes: Int,
    registro: Registro?,
    onAbrir: () -> Unit,
    onExcluir: () -> Unit
) {
    val resumo = remember(registro) {
        registro?.let { runCatching { calcularFatura(it.campos.paraEntrada()) }.getOrNull() }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(MESES[mes - 1], fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextoEscuro)

            if (resumo != null) {
                Text(
                    "Total: R$ ${resumo.total.reais()}",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = TextoEscuro
                )
                Text(
                    "Consumo ${resumo.consumo.kwh()} kWh  •  Saldo ${resumo.saldoFinal.kwh()} kWh",
                    fontSize = 13.sp,
                    color = Color.Gray
                )
            } else {
                Text(
                    if (registro != null) "Registro inválido (pode ser excluído)" else "Sem resultado salvo",
                    fontSize = 14.sp,
                    color = Color.Gray
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BotaoAmarelo("Abrir", Modifier.weight(1f), enabled = resumo != null, onClick = onAbrir)
                OutlinedButton(
                    onClick = onExcluir,
                    enabled = registro != null,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                ) {
                    Text("Excluir", fontSize = 15.sp)
                }
            }
        }
    }
}