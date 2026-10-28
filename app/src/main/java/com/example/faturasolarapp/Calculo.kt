package com.example.faturasolarapp   // <-- mantenha igual ao package do seu projeto

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
    val tarifaFixa: String = "",   // tarifa da linha de consumo fixo (custo de disponibilidade); vazio = base + bandeira
    val bandeira: String = "",
    val cosip: String = "",
    val bonusItaipu: String = "",   // desconto em R$ (opcional); vazio = 0
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
    val tarifaFixa: BigDecimal,
    val bandeira: BigDecimal,
    val cosip: BigDecimal,
    val bonusItaipu: BigDecimal,
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
    val tarifaFixa: BigDecimal,
    // valores (R$)
    val itemConsumoCompensado: BigDecimal,
    val itemConsumoCobrado: BigDecimal,
    val cosip: BigDecimal,
    val bonusItaipu: BigDecimal,
    val creditoGeracao: BigDecimal,
    val adicionalBandeira: BigDecimal,
    val adicionalBandeiraTeorico: BigDecimal,
    val total: BigDecimal
)

private fun String.numero(nome: String): BigDecimal =
    trim().replace(',', '.').toBigDecimalOrNull()
        ?: throw IllegalArgumentException("Preencha corretamente o campo: $nome")

/**
 * Converte os textos em números e valida. Lança IllegalArgumentException com a mensagem de erro.
 *
 * Tarifa fixa: é a tarifa da linha de consumo fixo da fatura (ex.: "Consumo 50 kWh a R$ 0,894560").
 * Ela muda de mês para mês. Se o campo ficar vazio, usa-se tarifa base + bandeira
 * (o mesmo cálculo de antes), então registros antigos continuam funcionando.
 */
fun Campos.paraEntrada(): EntradaFatura {
    val base = tarifaBase.numero("Tarifa base")
    val band = bandeira.numero("Bandeira")
    val fixa = if (tarifaFixa.isBlank()) base + band else tarifaFixa.numero("Tarifa fixa")
    // Bônus Itaipu: opcional; vazio = 0. É sempre DESCONTADO do total a pagar.
    val bonus = if (bonusItaipu.isBlank()) BigDecimal.ZERO else bonusItaipu.numero("Bônus Itaipu")

    val e = EntradaFatura(
        ativaAnterior = ativaAnt.numero("Ativa anterior"),
        ativaAtual = ativaAtual.numero("Ativa atual"),
        reversaAnterior = revAnt.numero("Reversa anterior"),
        reversaAtual = revAtual.numero("Reversa atual"),
        saldoAnterior = saldoAnt.numero("Acumulada anterior"),
        tarifaBase = base,
        tarifaFixa = fixa,
        bandeira = band,
        cosip = cosip.numero("COSIP"),
        bonusItaipu = bonus,
        ligacao = ligacao
    )
    require(e.ativaAtual >= e.ativaAnterior) {
        "A leitura atual (ativa) não pode ser menor que a anterior."
    }
    require(e.reversaAtual >= e.reversaAnterior) {
        "A leitura atual (reversa) não pode ser menor que a anterior."
    }
    require(e.bandeira >= BigDecimal.ZERO && e.bandeira <= BigDecimal("0.2")) {
        "A bandeira deve ser em R$/kWh (ex.: 0,01885), da tabela 'Bandeira Tarifária' da fatura. " +
                "O valor em R$ do 'Adicional Bandeira' (ex.: 0,93) não deve ser usado aqui."
    }
    require(e.tarifaFixa > BigDecimal.ZERO && e.tarifaFixa <= BigDecimal("5")) {
        "A tarifa fixa deve ser em R$/kWh (ex.: 0,894560), da linha de consumo fixo " +
                "(custo de disponibilidade) da fatura."
    }
    require(e.bonusItaipu >= BigDecimal.ZERO) {
        "O Bônus Itaipu deve ser informado como valor positivo (R$). Ele é descontado do total automaticamente."
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

    // Tarifa da linha de consumo fixo: informada pelo usuário (muda a cada mês)
    val tarifaFixa = e.tarifaFixa

    val itemCompensado = trunca(kwhCompensado * e.tarifaBase)
    val itemCobrado = trunca(kwhCobrado * tarifaFixa)
    val creditoGeracao = itemCompensado.negate()

    val adicionalBandeira = itemCobrado - arredonda(kwhCobrado * e.tarifaBase)
    val adicionalTeorico = kwhCobrado * e.bandeira

    // O adicional de bandeira NÃO entra na soma: já está dentro de itemCobrado
    // Bônus Itaipu: sempre descontado do valor total a pagar
    val bonusItaipu = arredonda(e.bonusItaipu)
    val total = itemCompensado + itemCobrado + creditoGeracao + e.cosip - bonusItaipu

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
        tarifaFixa = tarifaFixa,
        itemConsumoCompensado = itemCompensado,
        itemConsumoCobrado = itemCobrado,
        cosip = e.cosip,
        bonusItaipu = bonusItaipu,
        creditoGeracao = creditoGeracao,
        adicionalBandeira = adicionalBandeira,
        adicionalBandeiraTeorico = adicionalTeorico,
        total = total
    )
}