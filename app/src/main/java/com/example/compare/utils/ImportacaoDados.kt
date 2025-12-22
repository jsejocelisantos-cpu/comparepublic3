package com.example.compare.utils

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.example.compare.R
import com.google.firebase.firestore.FirebaseFirestore
import java.io.BufferedReader
import java.io.InputStreamReader

// Função para ler o CSV da pasta 'raw' e enviar para o Firestore
fun importarProdutosDoCsv(context: Context) {
    val db = FirebaseFirestore.getInstance()

    // Abre o arquivo 'produtos.csv' que está na pasta res/raw
    // Certifique-se que o nome do arquivo é 'produtos' (sem .csv no código)
    val inputStream = context.resources.openRawResource(R.raw.produtos)
    val reader = BufferedReader(InputStreamReader(inputStream))

    var linha: String? = reader.readLine() // Lê a primeira linha (cabeçalho) para pular

    var countSucesso = 0
    var countErro = 0

    Toast.makeText(context, "Iniciando importação...", Toast.LENGTH_SHORT).show()

    // Lê o resto do arquivo
    while (reader.readLine().also { linha = it } != null) {
        try {
            // Separa por vírgula (formato do nosso CSV)
            val colunas = linha!!.split(",")

            if (colunas.size >= 3) {
                val codigoBarras = colunas[0].trim()
                val nomeProduto = colunas[1].trim()
                val nomePesquisa = colunas[2].trim()

                if (codigoBarras.isNotEmpty() && nomeProduto.isNotEmpty()) {
                    // Cria o objeto para salvar
                    val produtoMap = hashMapOf(
                        "codigoBarras" to codigoBarras,
                        "nomeProduto" to nomeProduto,
                        "nomePesquisa" to nomePesquisa,
                        "dataCadastro" to com.google.firebase.Timestamp.now()
                    )

                    // Salva na coleção 'produtos_base' (separado das ofertas de usuários)
                    // Usamos o código de barras como ID do documento para evitar duplicatas
                    db.collection("produtos_base").document(codigoBarras)
                        .set(produtoMap)
                        .addOnSuccessListener { countSucesso++ }
                        .addOnFailureListener { countErro++ }
                }
            }
        } catch (e: Exception) {
            Log.e("Importacao", "Erro ao ler linha: $linha", e)
            countErro++
        }
    }

    // Aviso final (pode demorar um pouco para aparecer pois o processo é assíncrono)
    Toast.makeText(context, "Processando... Verifique o Logcat ou Firestore.", Toast.LENGTH_LONG).show()
}

class ImportacaoDados {
}