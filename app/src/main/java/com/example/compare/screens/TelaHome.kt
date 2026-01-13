package com.example.compare.screens

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.compare.model.DadosMercado
import com.example.compare.model.ItemLista
import com.example.compare.model.ProdutoPreco
import com.example.compare.utils.*
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelaHome(
    usuarioLogado: String,
    isAdmin: Boolean,
    mostrarBannerBoasVindas: Boolean,
    onFecharBanner: () -> Unit,
    estadoAtual: String,
    cidadeAtual: String,
    onMudarFiltro: (String, String) -> Unit,
    onIrCadastro: (ProdutoPreco?) -> Unit,
    onIrAdmin: () -> Unit,
    onIrListaCompras: () -> Unit,
    onSair: () -> Unit
) {
    // --- ESTADOS GLOBAIS ---
    var textoBusca by remember { mutableStateOf("") }
    val todosProdutos = remember { mutableStateListOf<ProdutoPreco>() }
    var isRefreshing by remember { mutableStateOf(false) }

    // Sugestões e Detalhes
    var sugestoesBusca by remember { mutableStateOf(emptyList<String>()) }
    var expandirSugestoes by remember { mutableStateOf(false) }
    var grupoSelecionadoParaDetalhes by remember { mutableStateOf<List<ProdutoPreco>?>(null) }

    // Controles de UI
    var carregandoDetalhes by remember { mutableStateOf(false) }
    var mostrarDialogoFiltro by remember { mutableStateOf(false) }
    var mostrarSobre by remember { mutableStateOf(false) }
    var mostrarSuporte by remember { mutableStateOf(false) }

    // Paginação
    var ultimoDocumento by remember { mutableStateOf<DocumentSnapshot?>(null) }
    var carregandoMais by remember { mutableStateOf(false) }
    var temMais by remember { mutableStateOf(true) }

    // Ferramentas
    val db = FirebaseFirestore.getInstance()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scanner = remember { GmsBarcodeScanning.getClient(context) }

    // ====================================================================================
    // --- LÓGICA DE NEGÓCIO ---
    // ====================================================================================

    fun adicionarAoCarrinho(produto: ProdutoPreco) {
        val item = ItemLista(
            usuarioId = usuarioLogado,
            nomeProduto = produto.nomeProduto,
            codigoBarras = produto.codigoBarras,
            quantidade = 1,
            comprado = false
        )
        db.collection("lista_compras")
            .whereEqualTo("usuarioId", usuarioLogado)
            .whereEqualTo("nomeProduto", produto.nomeProduto)
            .get()
            .addOnSuccessListener { res ->
                if (!res.isEmpty) {
                    val id = res.documents[0].id
                    val qtdAtual = res.documents[0].getLong("quantidade") ?: 1
                    db.collection("lista_compras").document(id).update("quantidade", qtdAtual + 1)
                    Toast.makeText(context, "+1 ${produto.nomeProduto}", Toast.LENGTH_SHORT).show()
                } else {
                    db.collection("lista_compras").add(item)
                    Toast.makeText(context, "Adicionado à lista!", Toast.LENGTH_SHORT).show()
                }
            }
    }

    fun carregarProdutos(resetar: Boolean = false, onConcluido: () -> Unit = {}) {
        if (carregandoMais) { onConcluido(); return }
        carregandoMais = true
        if (resetar) { todosProdutos.clear(); ultimoDocumento = null; temMais = true }

        var query = db.collection("ofertas").orderBy("data", Query.Direction.DESCENDING).limit(20)
        if (ultimoDocumento != null && !resetar) query = query.startAfter(ultimoDocumento!!)

        query.get().addOnSuccessListener { result ->
            if (!result.isEmpty) {
                if (resetar) ultimoDocumento = result.documents[result.size() - 1]
                for (doc in result) {
                    try {
                        val produto = doc.toObject(ProdutoPreco::class.java).copy(id = doc.id)
                        if (produto.cidade.equals(cidadeAtual, ignoreCase = true) || produto.cidade.isEmpty()) todosProdutos.add(produto)
                    } catch (e: Exception) {}
                }
                if (result.size() < 20) temMais = false
            } else { temMais = false }
            carregandoMais = false; onConcluido()
        }.addOnFailureListener { carregandoMais = false; onConcluido() }
    }

    fun finalizarBusca(termo: String, isBarcode: Boolean, onConcluido: () -> Unit) {
        if (isBarcode) {
            db.collection("produtos_base").document(termo).get().addOnSuccessListener { docBase ->
                if (docBase.exists()) {
                    val nome = docBase.getString("nomeProduto") ?: ""
                    val codigo = docBase.getString("codigoBarras") ?: ""
                    val prodCatalogo = ProdutoPreco(id = "CATALOGO_$codigo", nomeProduto = nome, codigoBarras = codigo, mercado = "Catálogo Global", valor = 0.0, usuarioId = "Sistema", data = Date(), cidade = "")
                    if (todosProdutos.none { it.codigoBarras == codigo }) todosProdutos.add(prodCatalogo)
                } else if (todosProdutos.isEmpty()) {
                    Toast.makeText(context, "Produto não encontrado. Cadastre agora!", Toast.LENGTH_LONG).show()
                    onIrCadastro(ProdutoPreco(codigoBarras = termo, nomeProduto = "", mercado = "", valor = 0.0, data = Date(), cidade = "", id = "", usuarioId = ""))
                }
                carregandoMais = false; temMais = false; onConcluido()
            }
        } else {
            db.collection("produtos_base")
                .whereGreaterThanOrEqualTo("nomePesquisa", termo)
                .whereLessThanOrEqualTo("nomePesquisa", termo + "\uf8ff")
                .limit(10).get().addOnSuccessListener { resBase ->
                    for (doc in resBase) {
                        val nome = doc.getString("nomeProduto") ?: ""
                        val codigo = doc.getString("codigoBarras") ?: ""
                        val prodCatalogo = ProdutoPreco(id = "CATALOGO_$codigo", nomeProduto = nome, codigoBarras = codigo, mercado = "Catálogo Global", valor = 0.0, usuarioId = "Sistema", data = Date(), cidade = "")
                        if (todosProdutos.none { it.nomeProduto == nome }) todosProdutos.add(prodCatalogo)
                    }
                    carregandoMais = false; temMais = false; onConcluido()
                }
        }
    }

    // --- BUSCA COM GEMINI ---
    fun buscarNoBanco(onConcluido: () -> Unit = {}) {
        expandirSugestoes = false
        if(textoBusca.isBlank()) { carregarProdutos(resetar = true, onConcluido = onConcluido); return }

        carregandoMais = true
        todosProdutos.clear()

        val termoBusca = textoBusca.lowercase().trim()
        val isBarcode = termoBusca.all { it.isDigit() } && termoBusca.length > 5

        val queryOfertas = if (isBarcode) {
            db.collection("ofertas").whereEqualTo("codigoBarras", termoBusca).limit(20)
        } else {
            db.collection("ofertas").whereGreaterThanOrEqualTo("nomePesquisa", termoBusca).whereLessThanOrEqualTo("nomePesquisa", termoBusca + "\uf8ff").limit(20)
        }

        queryOfertas.get().addOnSuccessListener { resOfertas ->
            val listaLocal = resOfertas.map { doc -> doc.toObject(ProdutoPreco::class.java).copy(id = doc.id) }
            val filtradosLocal = listaLocal.filter { it.cidade.equals(cidadeAtual, ignoreCase = true) || it.cidade.isEmpty() }
            todosProdutos.addAll(filtradosLocal)

            val temPrecoHoje = filtradosLocal.any { produto ->
                val hoje = Calendar.getInstance()
                val dataProd = Calendar.getInstance().apply { time = produto.data }
                hoje.get(Calendar.YEAR) == dataProd.get(Calendar.YEAR) && hoje.get(Calendar.DAY_OF_YEAR) == dataProd.get(Calendar.DAY_OF_YEAR)
            }

            if (temPrecoHoje || !isBarcode) {
                finalizarBusca(termoBusca, isBarcode, onConcluido)
            } else {
                Toast.makeText(context, "Consultando IA...", Toast.LENGTH_SHORT).show()
                scope.launch {
                    val resultadoIA = GeminiService.buscarPrecoOnline(termo = termoBusca, codigoBarras = if (isBarcode) termoBusca else "")
                    if (resultadoIA != null) {
                        val novaOfertaIA = ProdutoPreco(id = "", usuarioId = "IA_Gemini", nomeProduto = resultadoIA.nome, nomePesquisa = resultadoIA.nome.lowercase(), mercado = resultadoIA.mercado, valor = resultadoIA.valor, data = Date(), codigoBarras = if (isBarcode) termoBusca else "", fotoBase64 = "", comentario = "Preço encontrado via IA", estado = estadoAtual, cidade = cidadeAtual)
                        db.collection("ofertas").add(novaOfertaIA).addOnSuccessListener { docRef ->
                            todosProdutos.add(0, novaOfertaIA.copy(id = docRef.id))
                        }
                    }
                    finalizarBusca(termoBusca, isBarcode, onConcluido)
                }
            }
        }.addOnFailureListener { carregandoMais = false; onConcluido() }
    }

    // --- FUNÇÃO RECUPERADA: Atualizar a lista de detalhes ao editar ---
    fun atualizarDetalhesAbertos(produtoBase: ProdutoPreco) {
        val query = if (produtoBase.codigoBarras.isNotEmpty())
            db.collection("ofertas").whereEqualTo("codigoBarras", produtoBase.codigoBarras)
        else
            db.collection("ofertas").whereEqualTo("nomeProduto", produtoBase.nomeProduto)

        query.get().addOnSuccessListener { result ->
            val listaCompleta = result.map { doc -> doc.toObject(ProdutoPreco::class.java).copy(id = doc.id) }
            val listaFiltrada = listaCompleta.filter { it.cidade.equals(cidadeAtual, ignoreCase = true) || it.cidade.isEmpty() }

            if (listaFiltrada.isNotEmpty()) {
                grupoSelecionadoParaDetalhes = listaFiltrada
            } else {
                grupoSelecionadoParaDetalhes = null
            }
        }
    }

    fun carregarDetalhesCompletos(produtoBase: ProdutoPreco) {
        if (produtoBase.valor == 0.0 && produtoBase.mercado == "Catálogo Global") return
        carregandoDetalhes = true
        val query = if (produtoBase.codigoBarras.isNotEmpty()) db.collection("ofertas").whereEqualTo("codigoBarras", produtoBase.codigoBarras) else db.collection("ofertas").whereEqualTo("nomeProduto", produtoBase.nomeProduto)
        query.get().addOnSuccessListener { result ->
            val listaCompleta = result.map { doc -> doc.toObject(ProdutoPreco::class.java).copy(id = doc.id) }
            grupoSelecionadoParaDetalhes = listaCompleta.filter { it.cidade.equals(cidadeAtual, ignoreCase = true) || it.cidade.isEmpty() }
            carregandoDetalhes = false
        }.addOnFailureListener { carregandoDetalhes = false }
    }

    // --- AUTOCOMPLETE ---
    LaunchedEffect(textoBusca) {
        if (textoBusca.length >= 3) {
            delay(500)
            val termo = textoBusca.lowercase()
            db.collection("ofertas").whereGreaterThanOrEqualTo("nomePesquisa", termo).whereLessThanOrEqualTo("nomePesquisa", termo + "\uf8ff").limit(5).get().addOnSuccessListener { resOfertas ->
                val nomes = resOfertas.documents.mapNotNull { it.getString("nomeProduto") }.toMutableList()
                if (nomes.isNotEmpty()) { sugestoesBusca = nomes.distinct(); expandirSugestoes = true } else expandirSugestoes = false
            }
        } else expandirSugestoes = false
    }

    // --- CICLO DE VIDA E BACK PRESS ---
    var doubleBackToExitPressedOnce by remember { mutableStateOf(false) }
    BackHandler(enabled = true) {
        if (textoBusca.isNotEmpty()) {
            textoBusca = ""; expandirSugestoes = false; carregandoMais = true; todosProdutos.clear(); ultimoDocumento = null; temMais = true
            carregarProdutos(resetar = true)
        } else if (grupoSelecionadoParaDetalhes != null) {
            grupoSelecionadoParaDetalhes = null
        } else {
            if (doubleBackToExitPressedOnce) (context as? Activity)?.finish() else {
                doubleBackToExitPressedOnce = true; Toast.makeText(context, "Pressione voltar novamente para sair", Toast.LENGTH_SHORT).show()
                scope.launch { delay(2000); doubleBackToExitPressedOnce = false }
            }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME && textoBusca.isBlank()) carregarProdutos(resetar = true) }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(cidadeAtual) { carregarProdutos(resetar = true) }

    // ====================================================================================
    // --- UI (CHAMA O ARQUIVO TelaHomeUI.kt) ---
    // ====================================================================================

    Scaffold(
        topBar = {
            HomeTopBar(
                cidadeAtual = cidadeAtual,
                isAdmin = isAdmin,
                onIrListaCompras = onIrListaCompras,
                onIrAdmin = onIrAdmin,
                onMostrarFiltro = { mostrarDialogoFiltro = true },
                onMostrarSobre = { mostrarSobre = true },
                onMostrarSuporte = { mostrarSuporte = true },
                onSair = onSair
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onIrCadastro(null) }, containerColor = MaterialTheme.colorScheme.primary) { Icon(Icons.Default.Add, "Adicionar", tint = Color.White) }
        }
    ) { padding ->
        HomeContent(
            paddingValues = padding,
            todosProdutos = todosProdutos,
            isRefreshing = isRefreshing,
            textoBusca = textoBusca,
            sugestoesBusca = sugestoesBusca,
            expandirSugestoes = expandirSugestoes,
            temMais = temMais,
            carregandoMais = carregandoMais,
            carregandoDetalhes = carregandoDetalhes,
            onRefresh = { isRefreshing = true; if (textoBusca.isNotBlank()) buscarNoBanco { isRefreshing = false } else carregarProdutos(resetar = true) { isRefreshing = false } },
            onSearchChange = { textoBusca = it },
            onSearchSubmit = { expandirSugestoes = false; buscarNoBanco(); focusManager.clearFocus() },
            onSugestaoClick = { textoBusca = it; expandirSugestoes = false; buscarNoBanco(); focusManager.clearFocus() },
            onScanClick = {
                scanner.startScan().addOnSuccessListener { barcode -> val rawValue = barcode.rawValue; if (rawValue != null) { textoBusca = rawValue; expandirSugestoes = false; buscarNoBanco(); focusManager.clearFocus() } }
            },
            onCarregarMais = { carregarProdutos() },
            onCardClick = { carregarDetalhesCompletos(it) },
            onEditClick = { onIrCadastro(it.copy(id = "", mercado = "", valor = 0.0, usuarioId = "")) }, // Limpa mercado/ID para novo cadastro
            onCartClick = { adicionarAoCarrinho(it) }
        )
    }

    // --- DIALOGOS ---
    if (mostrarBannerBoasVindas) AlertDialog(onDismissRequest = onFecharBanner, icon = { Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.primary) }, title = { Text("Bem-vindo!") }, text = { Text("Ajude a comunidade a crescer e economizar adicionando e atualizando preços!", textAlign = TextAlign.Center) }, confirmButton = { Button(onClick = onFecharBanner, modifier = Modifier.fillMaxWidth()) { Text("Entendi, vamos lá!") } })
    if (mostrarSobre) AlertDialog(onDismissRequest = { mostrarSobre = false }, title = { Text("Sobre") }, text = { Text("Versão: 1.2 (Com IA Gemini)") }, confirmButton = { TextButton(onClick = { mostrarSobre = false }) { Text("Fechar") } })
    if (mostrarSuporte) DialogoSuporteUsuario(usuarioLogado = usuarioLogado, onDismiss = { mostrarSuporte = false })
    if (mostrarDialogoFiltro) DialogoLocalizacao(onDismiss = { mostrarDialogoFiltro = false }, onConfirmar = { est, cid -> onMudarFiltro(est, cid); mostrarDialogoFiltro = false })

    if (grupoSelecionadoParaDetalhes != null) {
        DialogoRankingDetalhes(
            grupoOfertas = grupoSelecionadoParaDetalhes!!,
            usuarioLogado = usuarioLogado,
            isAdmin = isAdmin,
            onDismiss = { grupoSelecionadoParaDetalhes = null },
            onIrCadastro = onIrCadastro,
            // CALLBACKS CORRIGIDOS: AGORA USAM atualizarDetalhesAbertos
            onDelete = { id -> db.collection("ofertas").document(id).delete().addOnSuccessListener { atualizarDetalhesAbertos(grupoSelecionadoParaDetalhes!!.first()) } },
            onUpdate = { nova -> db.collection("ofertas").document(nova.id).set(nova).addOnSuccessListener { atualizarDetalhesAbertos(nova) } },
            onNovoComentario = { id, txt -> if(!temOfensa(txt)) db.collection("ofertas").document(id).update("chatComentarios", grupoSelecionadoParaDetalhes!!.find { it.id == id }!!.chatComentarios + "$usuarioLogado: $txt").addOnSuccessListener { atualizarDetalhesAbertos(grupoSelecionadoParaDetalhes!!.first()) } },
            onApagarComentario = { id, txt -> val nova = grupoSelecionadoParaDetalhes!!.find { it.id == id }!!.chatComentarios.toMutableList().apply { remove(txt) }; db.collection("ofertas").document(id).update("chatComentarios", nova).addOnSuccessListener { atualizarDetalhesAbertos(grupoSelecionadoParaDetalhes!!.first()) } },
            onEditarComentario = { id, old, newTxt -> if(!temOfensa(newTxt)) { val nova = grupoSelecionadoParaDetalhes!!.find { it.id == id }!!.chatComentarios.toMutableList(); val idx = nova.indexOf(old); if(idx != -1) nova[idx] = "${old.split(":")[0]}: $newTxt"; db.collection("ofertas").document(id).update("chatComentarios", nova).addOnSuccessListener { atualizarDetalhesAbertos(grupoSelecionadoParaDetalhes!!.first()) } } },

            // ADMIN CALLBACKS
            onAtualizarNomeProduto = { novo -> val cap = capitalizarTextoHelper(novo); val words = cap.lowercase().split(" "); val pBase = grupoSelecionadoParaDetalhes!!.first(); val q = if (pBase.codigoBarras.isNotEmpty()) db.collection("ofertas").whereEqualTo("codigoBarras", pBase.codigoBarras) else db.collection("ofertas").whereEqualTo("nomeProduto", pBase.nomeProduto); q.get().addOnSuccessListener { res -> val b = db.batch(); res.forEach { b.update(it.reference, mapOf("nomeProduto" to cap, "nomePesquisa" to cap.lowercase(), "palavrasChave" to words)) }; b.commit().addOnSuccessListener { grupoSelecionadoParaDetalhes = null; carregarProdutos(resetar = true) } } },
            onAtualizarCodigoBarras = { novo -> val pBase = grupoSelecionadoParaDetalhes!!.first(); val q = if (pBase.codigoBarras.isNotEmpty()) db.collection("ofertas").whereEqualTo("codigoBarras", pBase.codigoBarras) else db.collection("ofertas").whereEqualTo("nomeProduto", pBase.nomeProduto); q.get().addOnSuccessListener { res -> val b = db.batch(); res.forEach { b.update(it.reference, "codigoBarras", novo) }; if(pBase.codigoBarras.isNotEmpty()) { db.collection("produtos_base").document(pBase.codigoBarras).get().addOnSuccessListener { s -> if(s.exists()){ val d = s.data!!; d["codigoBarras"] = novo; b.set(db.collection("produtos_base").document(novo), d); b.delete(s.reference) }; b.commit().addOnSuccessListener { grupoSelecionadoParaDetalhes = null; carregarProdutos(resetar = true) } } } else { b.commit().addOnSuccessListener { grupoSelecionadoParaDetalhes = null; carregarProdutos(resetar = true) } } } },
            onAtualizarNomeMercado = { ant, novo -> val nCap = capitalizarTextoHelper(novo); db.collection("ofertas").whereEqualTo("mercado", ant).get().addOnSuccessListener { res -> val b = db.batch(); res.forEach { b.update(it.reference, "mercado", nCap) }; db.collection("mercados").document(ant).get().addOnSuccessListener { doc -> if(doc.exists()) { val d = doc.toObject(DadosMercado::class.java)!!.copy(nome = nCap, id = nCap); b.set(db.collection("mercados").document(nCap), d); b.delete(doc.reference) }; b.commit().addOnSuccessListener { grupoSelecionadoParaDetalhes = null; carregarProdutos(resetar = true) } } } },
            onApagarMercado = { m -> db.collection("mercados").document(m).delete().addOnSuccessListener { Toast.makeText(context, "Mercado apagado", Toast.LENGTH_SHORT).show() } },
            onApagarProdutoGlobal = { p -> val q = if(p.codigoBarras.isNotEmpty()) db.collection("ofertas").whereEqualTo("codigoBarras", p.codigoBarras) else db.collection("ofertas").whereEqualTo("nomeProduto", p.nomeProduto); q.get().addOnSuccessListener { res -> val b = db.batch(); res.forEach { b.delete(it.reference) }; if(p.codigoBarras.isNotEmpty()) b.delete(db.collection("produtos_base").document(p.codigoBarras)); b.commit().addOnSuccessListener { grupoSelecionadoParaDetalhes = null; carregarProdutos(resetar = true) } } }
        )
    }
}

fun capitalizarTextoHelper(texto: String): String {
    return texto.trim().split("\\s+".toRegex()).joinToString(" ") { palavra ->
        palavra.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    }
}