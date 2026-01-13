package com.example.compare.utils

import android.util.Log
import com.example.compare.model.ProdutoPreco
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.util.Date

/**
 * Utilitário para raspar dados da Nota Fiscal de Consumidor Eletrônica (NFC-e)
 * Focado no layout da SEFAZ Santa Catarina (https://sat.sef.sc.gov.br)
 */
object NotaFiscalUtils {

    suspend fun importarNotaFiscalDaUrl(url: String): List<ProdutoPreco> {
        return withContext(Dispatchers.IO) {
            val listaProdutos = mutableListOf<ProdutoPreco>()

            try {
                // 1. Conecta ao site da nota (Timeout maior para garantir carregamento em 3G/4G)
                // O User-Agent é importante para o site achar que somos um celular comum e não um robô
                val doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Mobile Safari/537.36")
                    .timeout(15000)
                    .get()

                // 2. Tenta identificar o nome do Mercado (Razão Social ou Fantasia)
                // Em SC, geralmente fica no topo em <div class="txtTopo"> ou <label>
                var nomeMercado = doc.select(".txtTopo").first()?.text()
                if (nomeMercado.isNullOrEmpty()) {
                    nomeMercado = doc.select("#lblRazaoSocial").text() // Outro padrão comum
                }
                if (nomeMercado.isNullOrEmpty()) nomeMercado = "Mercado (Importado)"

                // 3. Seleciona as linhas da tabela de itens
                // O layout de SC geralmente usa uma tabela com id "tabResult" ou linhas com classe "item"
                // Estratégia: Pegar todas as TRs e filtrar as que parecem produtos
                val linhas = doc.select("tr")

                for (linha in linhas) {
                    try {
                        // --- EXTRAÇÃO DO NOME ---
                        // SC usa <span class="txtTit">NOME DO PRODUTO</span>
                        var nome = linha.select(".txtTit").text().trim()

                        // Fallback: Se não achar pela classe, tenta primeira célula se tiver texto longo
                        if (nome.isEmpty()) {
                            val primeiraCelula = linha.select("td").first()?.text() ?: ""
                            if (primeiraCelula.length > 5 && !primeiraCelula.contains("Código")) {
                                nome = primeiraCelula
                            }
                        }

                        // Se ainda não tem nome, pula essa linha (provavelmente é cabeçalho ou rodapé)
                        if (nome.isEmpty()) continue


                        // --- EXTRAÇÃO DO CÓDIGO (EAN/GTIN) ---
                        // SC usa <span class="RCod">Código: 789123456</span>
                        var codigoRaw = linha.select(".RCod").text()
                        var codigo = codigoRaw.replace(Regex("[^0-9]"), "") // Remove tudo que não é número

                        // Se falhou, tenta achar um número de 8 a 14 dígitos solto na linha (Regex)
                        if (codigo.length < 8) {
                            val textoCompleto = linha.text()
                            // Procura sequências de 8 a 14 dígitos (padrão EAN/GTIN)
                            val match = Regex("\\b\\d{8,14}\\b").find(textoCompleto)
                            codigo = match?.value ?: ""
                        }


                        // --- EXTRAÇÃO DO VALOR ---
                        // SC usa <span class="valor">10,90</span> ou está na última coluna
                        var valorTexto = linha.select(".valor").text()

                        // Se não achou classe .valor, pega a última célula da linha que tenha formato de dinheiro
                        if (valorTexto.isEmpty()) {
                            val celulas = linha.select("td")
                            if (celulas.isNotEmpty()) {
                                valorTexto = celulas.last()?.text() ?: ""
                            }
                        }

                        // Limpeza do valor (R$ 1.200,50 -> 1200.50)
                        val valor = valorTexto
                            .replace("R$", "")
                            .trim()
                            .replace(".", "")     // Remove ponto de milhar
                            .replace(",", ".")    // Troca vírgula decimal por ponto
                            .toDoubleOrNull() ?: 0.0


                        // --- VALIDAÇÃO FINAL ---
                        // Só adiciona se tivermos Nome e Preço. O código é desejável, mas se faltar, importamos mesmo assim.
                        if (nome.isNotEmpty() && valor > 0.0) {
                            listaProdutos.add(
                                ProdutoPreco(
                                    nomeProduto = capitalizarNome(nome),
                                    codigoBarras = codigo, // Pode vir vazio, o app trata depois gerando ID temporário
                                    valor = valor,
                                    mercado = capitalizarNome(nomeMercado),
                                    data = Date(),
                                    metodoEntrada = "QRCODE_SC"
                                )
                            )
                        }

                    } catch (e: Exception) {
                        Log.e("NotaFiscalUtils", "Erro ao processar linha: ${e.message}")
                        // Continua para a próxima linha mesmo se uma falhar
                    }
                }

            } catch (e: Exception) {
                e.printStackTrace()
                // Retorna lista vazia em caso de erro de conexão ou parse fatal
            }

            return@withContext listaProdutos
        }
    }

    // Função auxiliar para deixar o texto bonito (Ex: "ARROZ BRANCO" -> "Arroz Branco")
    private fun capitalizarNome(texto: String): String {
        return texto.trim().lowercase().split(" ").joinToString(" ") { palavra ->
            if (palavra.length > 2) palavra.replaceFirstChar { it.uppercase() } else palavra
        }
    }
}