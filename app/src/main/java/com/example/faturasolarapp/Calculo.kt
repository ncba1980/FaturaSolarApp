package com.example.faturasolarapp    // <-- mantenha o package do SEU projeto

import java.math.BigDecimal
import java.math.RoundingMode

/** Custo de disponibilidade (kWh/mês) por tipo de ligação. */
enum class Ligacao(val rotulo: String, val disponibilidade: Int) {
    MONOFASICA("Monofásica", 30),
    BIFASICA("Bifásica", 50),
    TRIFASICA("Trifásica", 100)
}

/** Textos digitados na tela (guardados como String para o usuário poder editar). */
data class Campos(
    val ativaAnt: String = "",
    val ativaAtual: String = "",
    val revAnt: String = "",
    val revAtual: String = "",
    val saldoAnt: String = "",
    val tarifaBase: String = "",
    val bandeira: String = "",
    val cosip: String = "",
    val ligacao: Ligacao = Ligacao.BIFASICA
)

/** Dados já convertidos para número. */
class EntradaFatura(
    val ativaAnterior: BigDecimal,
    val ativaAtual: BigDecimal,
    val reversaAnterior: BigDecimal,
    val reversaAtual: BigDecimal,
    val saldoAnterior: BigDecimal,
    val tarifaBase: BigDecimal,
    val bandeira: BigDecimal,
    val cosip: BigDecimal,
    val ligacao: Ligacao
)

/** Tudo o que o script Python imprimia. */
data class ResultadoFatura(
    // energia (kWh)
    val consumo: BigDecimal,
    val injetada: BigDecimal,
    val disponibilidade: BigDecimal,
    val aCompensar: BigDecimal,
    val creditoMes: BigDecimal,
    val residuo: BigDecimal,
    val saldoFinal: BigDecimal,
    val kwhCompensado: BigDecimal,
    val kwhCobrado: BigDecimal,
    // tarifas
    val tarifaBase: BigDecimal,
    val tarifaComBandeira: BigDecimal,
    // valores (R$)
    val itemConsumoCompensado: BigDecimal,
    val itemConsumoCobrado: BigDecimal,
    val cosip: BigDecimal,
    val creditoGeracao: BigDecimal,
    val adicionalBandeira: BigDecimal,
    val adicionalBandeiraTeorico: BigDecimal,
    val total: BigDecimal
)

private fun String.numero(nome: String): BigDecimal =
    trim().replace(',', '.').toBigDecimalOrNull()
        ?: throw IllegalArgumentException("Preencha corretamente o campo: $nome")

/** Converte os textos em números e valida. Lança IllegalArgumentException com a mensagem de erro. */
fun Campos.paraEntrada(): EntradaFatura {
    val e = EntradaFatura(
        ativaAnterior = ativaAnt.numero("Leitura anterior (energia ativa)"),
        ativaAtual = ativaAtual.numero("Leitura atual (energia ativa)"),
        reversaAnterior = revAnt.numero("Leitura anterior (energia reversa)"),
        reversaAtual = revAtual.numero("Leitura atual (energia reversa)"),
        saldoAnterior = saldoAnt.numero("Reserva acumulada anterior"),
        tarifaBase = tarifaBase.numero("Tarifa base"),
        bandeira = bandeira.numero("Bandeira"),
        cosip = cosip.numero("COSIP"),
        ligacao = ligacao
    )
    require(e.ativaAtual >= e.ativaAnterior) {
        "A leitura atual (ativa) não pode ser menor que a anterior."
    }
    require(e.reversaAtual >= e.reversaAnterior) {
        "A leitura atual (reversa) não pode ser menor que a anterior."
    }
    return e
}

/** Trunca em 2 casas (reproduz 44,728 -> 44,72 da fatura). */
private fun trunca(x: BigDecimal): BigDecimal = x.setScale(2, RoundingMode.DOWN)

private fun arredonda(x: BigDecimal): BigDecimal = x.setScale(2, RoundingMode.HALF_UP)

fun calcularFatura(e: EntradaFatura): ResultadoFatura {
    val zero = BigDecimal.ZERO

    val consumo = e.ativaAtual - e.ativaAnterior
    val injetada = e.reversaAtual - e.reversaAnterior
    val disp = BigDecimal(e.ligacao.disponibilidade)

    // A disponibilidade não pode ser abatida com créditos
    val aCompensar = maxOf(consumo - disp, zero)
    val creditoMes = minOf(injetada, aCompensar)
    val residuo = minOf(e.saldoAnterior, aCompensar - creditoMes)
    val creditoTotal = creditoMes + residuo
    val saldoFinal = e.saldoAnterior + injetada - creditoTotal

    val kwhCompensado = creditoTotal
    // Se o consumo for menor que a disponibilidade, cobra-se a disponibilidade
    val kwhCobrado = maxOf(consumo - creditoTotal, disp)

    val tarifaComBandeira = e.tarifaBase + e.bandeira

    val itemCompensado = trunca(kwhCompensado * e.tarifaBase)
    val itemCobrado = trunca(kwhCobrado * tarifaComBandeira)
    val creditoGeracao = itemCompensado.negate()

    val adicionalBandeira = itemCobrado - arredonda(kwhCobrado * e.tarifaBase)
    val adicionalTeorico = kwhCobrado * e.bandeira

    // O adicional de bandeira NÃO entra na soma: já está dentro de itemCobrado
    val total = itemCompensado + itemCobrado + creditoGeracao + e.cosip

    return ResultadoFatura(
        consumo = consumo,
        injetada = injetada,
        disponibilidade = disp,
        aCompensar = aCompensar,
        creditoMes = creditoMes,
        residuo = residuo,
        saldoFinal = saldoFinal,
        kwhCompensado = kwhCompensado,
        kwhCobrado = kwhCobrado,
        tarifaBase = e.tarifaBase,
        tarifaComBandeira = tarifaComBandeira,
        itemConsumoCompensado = itemCompensado,
        itemConsumoCobrado = itemCobrado,
        cosip = e.cosip,
        creditoGeracao = creditoGeracao,
        adicionalBandeira = adicionalBandeira,
        adicionalBandeiraTeorico = adicionalTeorico,
        total = total
    )
}