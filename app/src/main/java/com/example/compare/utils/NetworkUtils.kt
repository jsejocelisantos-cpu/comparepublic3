package com.example.compare.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

// Modelo simples para retorno
data class ProdutoExterno(
    val nome: String,
    val fotoUrl: String
)

suspend fun buscarProdutoOpenFoodFacts(codigoBarras: String): ProdutoExterno? {
    return withContext(Dispatchers.IO) {
        try {
            val url = URL("https://world.openfoodfacts.org/api/v0/product/$codigoBarras.json")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            if (connection.responseCode == 200) {
                val jsonString = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(jsonString)

                if (json.has("status") && json.getInt("status") == 1) {
                    val product = json.getJSONObject("product")
                    val nome = if (product.has("product_name")) product.getString("product_name") else ""
                    val foto = if (product.has("image_front_small_url")) product.getString("image_front_small_url") else ""

                    if (nome.isNotEmpty()) {
                        return@withContext ProdutoExterno(nome, foto)
                    }
                }
            }
            return@withContext null
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        }
    }
}