package com.example.compare.utils


import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

object GeminiService {
    // 1. Você precisa pegar essa chave em https://aistudio.google.com/
    private const val API_KEY = "SUA_API_KEY_AQUI"

    // Usamos o modelo Flash que é mais rápido e barato
    private val generativeModel = GenerativeModel(
        modelName = "gemini-1.5-flash",
        apiKey = API_KEY,
        generationConfig = generationConfig {
            responseMimeType = "application/json" // Força resposta em JSON limpo
        }
    )

    suspend fun buscarPrecoOnline(termo: String, codigoBarras: String): ResultadoIA? {
        return withContext(Dispatchers.IO) {
            try {
                // Prompt otimizado para Jaraguá do Sul
                val prompt = """
                    Atue como um assistente de compras em Jaraguá do Sul, SC.
                    O usuário está buscando o produto: "$termo" (Código EAN: $codigoBarras).
                    
                    Pesquise ou estime o preço atual deste produto em mercados comuns da região (Ex: Cooper, Angeloni, Fort Atacadista, Komprão).
                    
                    Retorne APENAS um objeto JSON com os dados mais prováveis ou encontrados:
                    {
                        "nome": "Nome completo do produto",
                        "mercado": "Nome do Mercado",
                        "valor": 10.99 (apenas numero ponto flutuante),
                        "encontrou": true
                    }
                    
                    Se não tiver certeza absoluta ou não encontrar, retorne: {"encontrou": false}
                """.trimIndent()

                val response = generativeModel.generateContent(prompt)
                val jsonString = response.text ?: return@withContext null

                // Parse do JSON
                val json = JSONObject(jsonString)
                if (json.getBoolean("encontrou")) {
                    return@withContext ResultadoIA(
                        nome = json.getString("nome"),
                        mercado = json.getString("mercado"),
                        valor = json.getDouble("valor")
                    )
                } else {
                    return@withContext null
                }

            } catch (e: Exception) {
                Log.e("Gemini", "Erro ao consultar IA: ${e.message}")
                return@withContext null
            }
        }
    }
}

data class ResultadoIA(
    val nome: String,
    val mercado: String,
    val valor: Double
)
