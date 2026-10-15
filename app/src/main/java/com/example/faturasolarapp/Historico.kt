package com.example.faturasolarapp   // <-- mantenha igual ao package do seu projeto

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

val MESES = listOf(
    "Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho",
    "Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro"
)

fun anoAtual(): Int = Calendar.getInstance().get(Calendar.YEAR)

/** Mês atual de 1 a 12. */
fun mesAtual(): Int = Calendar.getInstance().get(Calendar.MONTH) + 1

/**
 * Um resultado salvo. Guardamos os DADOS digitados (campos) e recalculamos o resultado
 * ao abrir, assim o resultado exibido é sempre idêntico ao do cálculo original.
 */
data class Registro(val ano: Int, val mes: Int, val campos: Campos)

/** Guarda os registros no próprio aparelho (SharedPreferences), sem internet e sem dependências extras. */
class RepositorioHistorico(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("fatura_solar_historico", Context.MODE_PRIVATE)

    fun todos(): List<Registro> {
        val texto = prefs.getString(CHAVE, null) ?: return emptyList()
        return try {
            val arr = JSONArray(texto)
            (0 until arr.length()).map { arr.getJSONObject(it).paraRegistro() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Salva; se já existir resultado do mesmo mês/ano, ele é substituído. */
    fun salvar(registro: Registro) {
        val nova = todos().filterNot { it.ano == registro.ano && it.mes == registro.mes } + registro
        gravar(nova)
    }

    fun excluir(ano: Int, mes: Int) {
        gravar(todos().filterNot { it.ano == ano && it.mes == mes })
    }

    private fun gravar(lista: List<Registro>) {
        val arr = JSONArray()
        lista.forEach { arr.put(it.paraJson()) }
        prefs.edit().putString(CHAVE, arr.toString()).apply()
    }

    private companion object {
        const val CHAVE = "registros"
    }
}

private fun Registro.paraJson(): JSONObject = JSONObject().apply {
    put("ano", ano)
    put("mes", mes)
    put("ativaAnt", campos.ativaAnt)
    put("ativaAtual", campos.ativaAtual)
    put("revAnt", campos.revAnt)
    put("revAtual", campos.revAtual)
    put("saldoAnt", campos.saldoAnt)
    put("tarifaBase", campos.tarifaBase)
    put("bandeira", campos.bandeira)
    put("cosip", campos.cosip)
    put("ligacao", campos.ligacao.name)
}

private fun JSONObject.paraRegistro(): Registro = Registro(
    ano = getInt("ano"),
    mes = getInt("mes"),
    campos = Campos(
        ativaAnt = getString("ativaAnt"),
        ativaAtual = getString("ativaAtual"),
        revAnt = getString("revAnt"),
        revAtual = getString("revAtual"),
        saldoAnt = getString("saldoAnt"),
        tarifaBase = getString("tarifaBase"),
        bandeira = getString("bandeira"),
        cosip = getString("cosip"),
        ligacao = Ligacao.valueOf(getString("ligacao"))
    )
)