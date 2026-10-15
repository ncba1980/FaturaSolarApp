package com.example.faturasolarapp   // <-- mantenha o package do SEU projeto

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.cos
import kotlin.math.sin

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

// ------------------------------------------------------------------ Navegação
enum class Tela { INICIAL, DADOS, RESULTADO }

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

private val AzulTopo = Color(0xFF4FAEF5)
private val AzulBase = Color(0xFFB5DCF8)
private val Amarelo = Color(0xFFFFC107)
private val TextoEscuro = Color(0xFF1A2B3C)

@Composable
fun FaturaSolarApp() {
    var tela by rememberSaveable { mutableStateOf(Tela.INICIAL) }
    var campos by rememberSaveable(stateSaver = CamposSaver) { mutableStateOf(Campos()) }

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
                onCalcular = { tela = Tela.RESULTADO }
            )

            Tela.RESULTADO -> {
                val resultado = remember(campos) {
                    runCatching { calcularFatura(campos.paraEntrada()) }.getOrNull()
                }
                if (resultado == null) {
                    tela = Tela.DADOS
                } else {
                    TelaResultado(
                        r = resultado,
                        onNovaConsulta = { tela = Tela.DADOS }
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------------------ Logo (desenhado em código)
@Composable
fun Logo(tamanho: Dp) {
    Canvas(
        modifier = Modifier
            .size(tamanho)
            .clip(RoundedCornerShape(tamanho * 0.2f))
            .background(Color.White)
    ) {
        val w = size.width
        val h = size.height
        val centro = Offset(w * 0.45f, h * 0.45f)

        // raios do sol
        for (i in 0..8) {
            val ang = Math.toRadians(180.0 + i * 22.5)
            val dx = cos(ang).toFloat()
            val dy = sin(ang).toFloat()
            drawLine(
                color = Color(0xFFFFB300),
                start = Offset(centro.x + dx * w * 0.27f, centro.y + dy * w * 0.27f),
                end = Offset(centro.x + dx * w * 0.38f, centro.y + dy * w * 0.38f),
                strokeWidth = w * 0.035f,
                cap = StrokeCap.Round
            )
        }
        // sol
        drawCircle(Color(0xFFFFB300), radius = w * 0.2f, center = centro)

        // casa
        drawRect(
            Color(0xFF1F5FA8),
            topLeft = Offset(w * 0.60f, h * 0.55f),
            size = Size(w * 0.22f, h * 0.22f)
        )
        val telhado = Path().apply {
            moveTo(w * 0.57f, h * 0.55f)
            lineTo(w * 0.71f, h * 0.42f)
            lineTo(w * 0.85f, h * 0.55f)
            close()
        }
        drawPath(telhado, Color(0xFF174A85))

        // colina + painéis
        drawOval(
            Color(0xFF2E8B3A),
            topLeft = Offset(-w * 0.25f, h * 0.68f),
            size = Size(w * 1.5f, h * 0.9f)
        )
        drawRect(
            Color(0xFF43B04F),
            topLeft = Offset(w * 0.12f, h * 0.60f),
            size = Size(w * 0.38f, h * 0.12f)
        )
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable { onContinuar() }
    ) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Logo(180.dp)
            Spacer(Modifier.height(16.dp))
            Text(
                "Fatura Solar App",
                color = Color.White,
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Text(
            "By Nadson Cley B. de Araújo",
            color = TextoEscuro,
            fontSize = 16.sp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 40.dp)
        )
    }
}

// ------------------------------------------------------------------ Tela 2: dados
@Composable
fun TelaDados(
    campos: Campos,
    onChange: (Campos) -> Unit,
    onCalcular: () -> Unit
) {
    var erro by rememberSaveable { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Logo(90.dp)
        Text(
            "Fatura Solar App",
            color = Color.White,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold
        )

        Campo("Leitura anterior - energia ativa (kWh)", campos.ativaAnt) {
            onChange(campos.copy(ativaAnt = it))
        }
        Campo("Leitura atual - energia ativa (kWh)", campos.ativaAtual) {
            onChange(campos.copy(ativaAtual = it))
        }
        Campo("Leitura anterior - energia reversa (kWh)", campos.revAnt) {
            onChange(campos.copy(revAnt = it))
        }
        Campo("Leitura atual - energia reversa (kWh)", campos.revAtual) {
            onChange(campos.copy(revAtual = it))
        }
        Campo("Reserva acumulada anterior (kWh)", campos.saldoAnt) {
            onChange(campos.copy(saldoAnt = it))
        }
        Campo("Tarifa base (R$/kWh)", campos.tarifaBase) {
            onChange(campos.copy(tarifaBase = it))
        }
        Campo("Bandeira (R$/kWh)", campos.bandeira) {
            onChange(campos.copy(bandeira = it))
        }
        Campo("COSIP (R$)", campos.cosip) {
            onChange(campos.copy(cosip = it))
        }

        // Tipo de ligação
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                Text(
                    "Tipo de ligação (custo de disponibilidade)",
                    fontSize = 13.sp,
                    color = TextoEscuro,
                    modifier = Modifier.padding(start = 8.dp, top = 4.dp)
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Ligacao.values().forEach { opcao ->
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onChange(campos.copy(ligacao = opcao)) },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = campos.ligacao == opcao,
                                onClick = { onChange(campos.copy(ligacao = opcao)) }
                            )
                            Text(
                                "${opcao.rotulo}\n(${opcao.disponibilidade} kWh)",
                                fontSize = 12.sp,
                                color = TextoEscuro
                            )
                        }
                    }
                }
            }
        }

        Button(
            onClick = {
                runCatching { campos.paraEntrada() }
                    .onSuccess {
                        erro = null
                        onCalcular()
                    }
                    .onFailure { erro = it.message }
            },
            colors = ButtonDefaults.buttonColors(containerColor = Amarelo, contentColor = TextoEscuro),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Text("Calcular", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }

        Button(
            onClick = {
                onChange(Campos())
                erro = null
            },
            colors = ButtonDefaults.buttonColors(containerColor = Amarelo, contentColor = TextoEscuro),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Text("Limpar", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFDF5))
        ) {
            Text(
                text = erro ?: "O resultado aparecerá na próxima tela",
                color = if (erro != null) Color(0xFFB00020) else TextoEscuro,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            )
        }
    }
}

@Composable
private fun Campo(rotulo: String, valor: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = valor,
        onValueChange = onChange,
        label = { Text(rotulo) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Color.White,
            unfocusedContainerColor = Color.White
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

// ------------------------------------------------------------------ Tela 3: resultado
private fun BigDecimal.kwh(): String =
    stripTrailingZeros().toPlainString().replace('.', ',')

private fun BigDecimal.reais(): String =
    setScale(2, RoundingMode.HALF_UP).toPlainString().replace('.', ',')

private fun BigDecimal.tarifa(): String =
    setScale(6, RoundingMode.HALF_UP).toPlainString().replace('.', ',')

@Composable
fun TelaResultado(r: ResultadoFatura, onNovaConsulta: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Logo(80.dp)
        Text(
            "Resultado da fatura",
            color = Color.White,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold
        )

        Secao("Energia (kWh)") {
            Linha("Consumo medido (ativa)", "${r.consumo.kwh()} kWh")
            Linha("Energia injetada no mês (reversa)", "${r.injetada.kwh()} kWh")
            Linha("Custo de disponibilidade", "${r.disponibilidade.kwh()} kWh")
            Linha("kWh a compensar", "${r.aCompensar.kwh()} kWh")
            Linha("   crédito do mês", "${r.creditoMes.kwh()} kWh")
            Linha("   Resíduo En Reversa (do saldo)", "${r.residuo.kwh()} kWh")
            Linha("Saldo acumulado após compensação", "${r.saldoFinal.kwh()} kWh")
        }

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
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            Linha(
                "Adicional Bandeira (informativo, já incluso)",
                "R$ ${r.adicionalBandeira.reais()}"
            )
            Text(
                "Cálculo teórico da bandeira: ${r.adicionalBandeiraTeorico.setScale(4, RoundingMode.HALF_UP).toPlainString().replace('.', ',')}",
                fontSize = 12.sp,
                color = Color.Gray
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3C4))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("TOTAL A PAGAR", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextoEscuro)
                Text("R$ ${r.total.reais()}", fontWeight = FontWeight.Bold, fontSize = 22.sp, color = TextoEscuro)
            }
        }

        Button(
            onClick = onNovaConsulta,
            colors = ButtonDefaults.buttonColors(containerColor = Amarelo, contentColor = TextoEscuro),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Text("Voltar / Nova consulta", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun Secao(titulo: String, conteudo: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(titulo, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextoEscuro)
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
        Text(rotulo, modifier = Modifier.weight(1f), fontSize = 14.sp, color = TextoEscuro)
        Spacer(Modifier.size(8.dp))
        Text(valor, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = TextoEscuro)
    }
}