package com.example.compare

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import com.example.compare.model.ProdutoPreco
import com.example.compare.screens.TelaAdmin
import com.example.compare.screens.TelaCadastro
import com.example.compare.screens.TelaHome
import com.example.compare.screens.TelaListaCompras
import com.example.compare.screens.TelaLogin

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // CORES DO TEMA ESCURO (INDUSTRIAL)
        val AzulIndustrial = Color(0xFF1565C0)
        val AzulAcento = Color(0xFF42A5F5)
        val FundoEscuro = Color(0xFF121212)
        val SuperficieCard = Color(0xFF252525)

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = AzulIndustrial,
                    onPrimary = Color.White,
                    secondary = AzulAcento,
                    onSecondary = Color.White,
                    background = FundoEscuro,
                    onBackground = Color(0xFFE6E1E5),
                    surface = SuperficieCard,
                    onSurface = Color(0xFFE6E1E5)
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize().safeDrawingPadding(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavegacao()
                }
            }
        }
    }
}

@Composable
fun AppNavegacao() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("app_compare_prefs", Context.MODE_PRIVATE) }

    // Estados de Sessão e Configuração
    var nomeUsuario by remember { mutableStateOf(prefs.getString("usuario", "") ?: "") }
    var isAdmin by remember { mutableStateOf(prefs.getBoolean("admin", false)) }
    var aceitouTermos by remember { mutableStateOf(prefs.getBoolean("termos_aceitos", false)) }
    var exibirBannerInicial by remember { mutableStateOf(true) }

    // Navegação e Dados Temporários
    var telaAtual by remember { mutableStateOf(if (nomeUsuario.isNotEmpty()) "HOME" else "LOGIN") }
    var produtoParaPreencher by remember { mutableStateOf<ProdutoPreco?>(null) }

    // Filtros de Localização Global
    var estadoSelecionado by remember { mutableStateOf("SC") }
    var cidadeSelecionada by remember { mutableStateOf("Jaraguá do Sul") }

    // --- NOVO: Memória do último mercado utilizado na sessão ---
    var ultimoMercadoUtilizado by remember { mutableStateOf("") }

    // Dialogo de Termos de Uso (Aparece apenas na primeira vez)
    if (telaAtual == "HOME" && !aceitouTermos) {
        AlertDialog(
            onDismissRequest = {}, // Impede fechar clicando fora
            title = { Text("Termos de Uso") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text("Bem-vindo ao Compare!\n\n", fontWeight = FontWeight.Bold)
                    Text("1. Colaboração: Os preços são informados por usuários e servem apenas como referência.\n")
                    Text("2. Isenção: Não garantimos a precisão exata dos valores ou estoques.\n")
                    Text("3. Marcas: Nomes comerciais são usados apenas para identificação do produto.\n")
                    Text("4. Conduta: Conteúdo ofensivo resultará em banimento.\n")
                    Text("\nAo continuar, você concorda com estes termos.")
                }
            },
            confirmButton = {
                Button(onClick = {
                    aceitouTermos = true
                    prefs.edit().putBoolean("termos_aceitos", true).apply()
                }) { Text("Li e Concordo") }
            }
        )
    }

    // Gerenciador de Telas
    when (telaAtual) {
        "LOGIN" -> {
            TelaLogin(
                onLoginSucesso = { nome, admin ->
                    nomeUsuario = nome
                    isAdmin = admin
                    // Salva sessão
                    prefs.edit().putString("usuario", nome).putBoolean("admin", admin).apply()
                    exibirBannerInicial = true
                    telaAtual = "HOME"
                }
            )
        }
        "HOME" -> {
            TelaHome(
                usuarioLogado = nomeUsuario,
                isAdmin = isAdmin,
                mostrarBannerBoasVindas = exibirBannerInicial,
                onFecharBanner = { exibirBannerInicial = false },
                estadoAtual = estadoSelecionado,
                cidadeAtual = cidadeSelecionada,
                onMudarFiltro = { est, cid ->
                    estadoSelecionado = est
                    cidadeSelecionada = cid
                },
                onIrCadastro = { produtoOpcional ->
                    produtoParaPreencher = produtoOpcional
                    telaAtual = "CADASTRO"
                },
                onIrAdmin = {
                    telaAtual = "ADMIN"
                },
                onIrListaCompras = {
                    telaAtual = "LISTA_COMPRAS"
                },
                onSair = {
                    // Limpa sessão
                    prefs.edit().clear().apply()
                    nomeUsuario = ""
                    isAdmin = false
                    aceitouTermos = false
                    telaAtual = "LOGIN"
                }
            )
        }
        "CADASTRO" -> {
            BackHandler {
                produtoParaPreencher = null
                telaAtual = "HOME"
            }
            TelaCadastro(
                usuarioNome = nomeUsuario,
                produtoPreenchido = produtoParaPreencher,
                estadoPre = estadoSelecionado,
                cidadePre = cidadeSelecionada,
                ultimoMercado = ultimoMercadoUtilizado, // Passa a memória para a tela
                onVoltar = {
                    produtoParaPreencher = null
                    telaAtual = "HOME"
                },
                onSalvar = { mercadoSalvo ->
                    // Atualiza a memória com o mercado que o usuário acabou de usar
                    ultimoMercadoUtilizado = mercadoSalvo
                    produtoParaPreencher = null
                    telaAtual = "HOME"
                }
            )
        }
        "ADMIN" -> {
            BackHandler {
                telaAtual = "HOME"
            }
            TelaAdmin(
                onVoltar = { telaAtual = "HOME" }
            )
        }
        "LISTA_COMPRAS" -> {
            BackHandler {
                telaAtual = "HOME"
            }
            TelaListaCompras(
                usuarioLogado = nomeUsuario,
                cidadeAtual = cidadeSelecionada,
                onVoltar = { telaAtual = "HOME" }
            )
        }
    }
}