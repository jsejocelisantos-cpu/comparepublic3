package com.example.compare.screens

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.compare.R
import com.example.compare.model.ItemLista
import com.example.compare.model.ProdutoPreco
import com.example.compare.model.Usuario
import com.example.compare.utils.*
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Date

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
    var textoBusca by remember { mutableStateOf("") }
    val todosProdutos = remember { mutableStateListOf<ProdutoPreco>() }

    // Estado do Swipe to Refresh
    var isRefreshing by remember { mutableStateOf(false) }

    var sugestoesBusca by remember { mutableStateOf(emptyList<String>()) }
    var expandirSugestoes by remember { mutableStateOf(false) }

    var grupoSelecionadoParaDetalhes by remember { mutableStateOf<List<ProdutoPreco>?>(null) }
    var carregandoDetalhes by remember { mutableStateOf(false) }
    var mostrarDialogoFiltro by remember { mutableStateOf(false) }

    var menuExpandido by remember { mutableStateOf(false) }
    var mostrarSobre by remember { mutableStateOf(false) }
    var mostrarSuporte by remember { mutableStateOf(false) }

    var ultimoDocumento by remember { mutableStateOf<DocumentSnapshot?>(null) }
    var carregandoMais by remember { mutableStateOf(false) }
    var temMais by remember { mutableStateOf(true) }

    val db = FirebaseFirestore.getInstance()
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scanner = remember { GmsBarcodeScanning.getClient(context) }

    // --- ADICIONAR AO CARRINHO ---
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

    // --- AUTOCOMPLETE DA BUSCA ---
    LaunchedEffect(textoBusca) {
        if (textoBusca.length >= 3) {
            delay(500)
            val termo = textoBusca.lowercase()

            // Busca nas ofertas
            db.collection("ofertas")
                .whereGreaterThanOrEqualTo("nomePesquisa", termo)
                .whereLessThanOrEqualTo("nomePesquisa", termo + "\uf8ff")
                .limit(5).get().addOnSuccessListener { resOfertas ->
                    val nomes = resOfertas.documents.mapNotNull { it.getString("nomeProduto") }.toMutableList()

                    // Busca no catálogo (produtos_base)
                    db.collection("produtos_base")
                        .whereGreaterThanOrEqualTo("nomePesquisa", termo)
                        .whereLessThanOrEqualTo("nomePesquisa", termo + "\uf8ff")
                        .limit(5).get().addOnSuccessListener { resBase ->
                            val nomesBase = resBase.documents.mapNotNull { it.getString("nomeProduto") }
                            nomes.addAll(nomesBase)
                            val nomesUnicos = nomes.distinct()
                            if (nomesUnicos.isNotEmpty()) { sugestoesBusca = nomesUnicos; expandirSugestoes = true }
                            else { expandirSugestoes = false }
                        }
                }
        } else { expandirSugestoes = false }
    }

    // --- VOLTAR (SAIR OU LIMPAR BUSCA) ---
    var doubleBackToExitPressedOnce by remember { mutableStateOf(false) }
    BackHandler(enabled = true) {
        if (textoBusca.isNotEmpty()) {
            textoBusca = ""; expandirSugestoes = false; carregandoMais = true; todosProdutos.clear(); ultimoDocumento = null; temMais = true
            // Recarrega feed inicial
            db.collection("ofertas").orderBy("data", Query.Direction.DESCENDING).limit(20).get().addOnSuccessListener { result ->
                for (doc in result) {
                    try {
                        val produto = doc.toObject(ProdutoPreco::class.java).copy(id = doc.id)
                        if (produto.cidade.equals(cidadeAtual, ignoreCase = true) || produto.cidade.isEmpty()) todosProdutos.add(produto)
                    } catch (e: Exception) {}
                }
                if(!result.isEmpty) ultimoDocumento = result.documents[result.size() - 1]; carregandoMais = false
            }
        } else if (grupoSelecionadoParaDetalhes != null) {
            grupoSelecionadoParaDetalhes = null
        } else {
            if (doubleBackToExitPressedOnce) (context as? Activity)?.finish() else {
                doubleBackToExitPressedOnce = true; Toast.makeText(context, "Pressione voltar novamente para sair", Toast.LENGTH_SHORT).show()
                scope.launch { delay(2000); doubleBackToExitPressedOnce = false }
            }
        }
    }

    // --- CARREGAR PRODUTOS (FEED INICIAL) ---
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

    LaunchedEffect(cidadeAtual) { carregarProdutos(resetar = true) }

    // --- BUSCA (HÍBRIDA: OFERTAS + CATÁLOGO) ---
    fun buscarNoBanco(onConcluido: () -> Unit = {}) {
        expandirSugestoes = false
        if(textoBusca.isBlank()) { carregarProdutos(resetar = true, onConcluido = onConcluido); return }

        carregandoMais = true
        todosProdutos.clear()

        val termoBusca = textoBusca.lowercase()
        val isBarcode = termoBusca.all { it.isDigit() } && termoBusca.length > 5

        // 1. Busca Ofertas
        val queryOfertas = if (isBarcode) {
            db.collection("ofertas").whereEqualTo("codigoBarras", termoBusca).limit(20)
        } else {
            db.collection("ofertas").whereGreaterThanOrEqualTo("nomePesquisa", termoBusca).whereLessThanOrEqualTo("nomePesquisa", termoBusca + "\uf8ff").limit(20)
        }

        queryOfertas.get().addOnSuccessListener { resOfertas ->
            for(doc in resOfertas) {
                val p = doc.toObject(ProdutoPreco::class.java).copy(id = doc.id)
                if (p.cidade.equals(cidadeAtual, ignoreCase = true) || p.cidade.isEmpty()) todosProdutos.add(p)
            }

            // 2. Busca Catálogo (Se não achou nas ofertas ou para complementar)
            if (isBarcode) {
                db.collection("produtos_base").document(termoBusca).get().addOnSuccessListener { docBase ->
                    if (docBase.exists()) {
                        val nome = docBase.getString("nomeProduto") ?: ""
                        val codigo = docBase.getString("codigoBarras") ?: ""
                        // Cria objeto visual do catálogo (sem preço)
                        val prodCatalogo = ProdutoPreco(id = "CATALOGO_$codigo", nomeProduto = nome, codigoBarras = codigo, mercado = "Catálogo Global", valor = 0.0, usuarioId = "Sistema", data = Date(), cidade = "")

                        // Só adiciona se não tiver oferta ativa (para não duplicar visualmente)
                        if (todosProdutos.none { it.codigoBarras == codigo }) todosProdutos.add(prodCatalogo)
                    } else {
                        // --- PRODUTO NÃO ENCONTRADO EM LUGAR NENHUM ---
                        if (todosProdutos.isEmpty()) {
                            Toast.makeText(context, "Produto não cadastrado. Cadastre agora!", Toast.LENGTH_LONG).show()
                            // Abre o cadastro limpando o ID para criar um novo
                            onIrCadastro(ProdutoPreco(
                                codigoBarras = termoBusca,
                                nomeProduto = "",
                                mercado = "", // Vai vazio para usar o último
                                valor = 0.0,
                                data = Date(),
                                cidade = "",
                                id = "", // Garante que é novo
                                usuarioId = ""
                            ))
                        }
                    }
                    carregandoMais = false; temMais = false; onConcluido()
                }
            } else {
                db.collection("produtos_base")
                    .whereGreaterThanOrEqualTo("nomePesquisa", termoBusca)
                    .whereLessThanOrEqualTo("nomePesquisa", termoBusca + "\uf8ff")
                    .limit(10)
                    .get().addOnSuccessListener { resBase ->
                        for (doc in resBase) {
                            val nome = doc.getString("nomeProduto") ?: ""
                            val codigo = doc.getString("codigoBarras") ?: ""
                            val prodCatalogo = ProdutoPreco(id = "CATALOGO_$codigo", nomeProduto = nome, codigoBarras = codigo, mercado = "Catálogo Global", valor = 0.0, usuarioId = "Sistema", data = Date(), cidade = "")
                            if (todosProdutos.none { it.nomeProduto == nome }) todosProdutos.add(prodCatalogo)
                        }
                        carregandoMais = false; temMais = false; onConcluido()
                    }
            }
        }.addOnFailureListener { carregandoMais = false; onConcluido() }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME && textoBusca.isBlank()) carregarProdutos(resetar = true) }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun carregarDetalhesCompletos(produtoBase: ProdutoPreco) {
        // Se for catálogo (sem preço), não abre detalhes, a ação é pelo botão de lápis/carrinho
        if (produtoBase.valor == 0.0 && produtoBase.mercado == "Catálogo Global") return

        carregandoDetalhes = true
        val query = if (produtoBase.codigoBarras.isNotEmpty()) db.collection("ofertas").whereEqualTo("codigoBarras", produtoBase.codigoBarras) else db.collection("ofertas").whereEqualTo("nomeProduto", produtoBase.nomeProduto)
        query.get().addOnSuccessListener { result ->
            val listaCompleta = result.map { doc -> doc.toObject(ProdutoPreco::class.java).copy(id = doc.id) }
            grupoSelecionadoParaDetalhes = listaCompleta.filter { it.cidade.equals(cidadeAtual, ignoreCase = true) || it.cidade.isEmpty() }
            carregandoDetalhes = false
        }.addOnFailureListener { carregandoDetalhes = false }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(painter = painterResource(id = R.drawable.logohome2), contentDescription = "Logo", modifier = Modifier.size(180.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Column { Text(cidadeAtual, fontSize = 12.sp, fontWeight = FontWeight.Normal) }
                    }
                },
                actions = {
                    IconButton(onClick = { menuExpandido = true }) { Icon(Icons.Default.MoreVert, "Menu") }
                    DropdownMenu(expanded = menuExpandido, onDismissRequest = { menuExpandido = false }) {
                        DropdownMenuItem(text = { Text("Lista de Compras") }, leadingIcon = { Icon(Icons.Default.ShoppingCart, null) }, onClick = { menuExpandido = false; onIrListaCompras() })
                        DropdownMenuItem(text = { Text("Alterar Cidade") }, leadingIcon = { Icon(Icons.Default.LocationOn, null) }, onClick = { menuExpandido = false; mostrarDialogoFiltro = true })
                        Divider()
                        if(isAdmin) DropdownMenuItem(text = { Text("Painel Admin") }, onClick = { menuExpandido = false; onIrAdmin() })
                        DropdownMenuItem(text = { Text("Sobre o App") }, onClick = { menuExpandido = false; mostrarSobre = true })
                        DropdownMenuItem(text = { Text("Suporte") }, onClick = { menuExpandido = false; mostrarSuporte = true })
                        Divider()
                        DropdownMenuItem(text = { Text("Sair", color = Color.Red) }, onClick = { menuExpandido = false; onSair() })
                    }
                }
            )
        },
        floatingActionButton = { FloatingActionButton(onClick = { onIrCadastro(null) }) { Icon(Icons.Default.Add, "Adicionar") } }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                isRefreshing = true
                if (textoBusca.isNotBlank()) buscarNoBanco { isRefreshing = false }
                else carregarProdutos(resetar = true) { isRefreshing = false }
            },
            modifier = Modifier.padding(padding).fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Column {
                    // BARRA DE BUSCA
                    Box(modifier = Modifier.fillMaxWidth().padding(8.dp).zIndex(1f)) {
                        OutlinedTextField(
                            value = textoBusca, onValueChange = { textoBusca = it }, label = { Text("Buscar produto...") }, leadingIcon = { Icon(Icons.Default.Search, null) },
                            trailingIcon = {
                                Image(
                                    painter = painterResource(id = R.drawable.scancode), contentDescription = "Escanear", contentScale = ContentScale.Fit,
                                    modifier = Modifier.size(50.dp).padding(end = 8.dp).clip(RoundedCornerShape(4.dp)).clickable {
                                        scanner.startScan().addOnSuccessListener { barcode -> val rawValue = barcode.rawValue; if (rawValue != null) { textoBusca = rawValue; expandirSugestoes = false; buscarNoBanco(); focusManager.clearFocus() } }
                                            .addOnFailureListener { Toast.makeText(context, "Erro ao abrir câmera", Toast.LENGTH_SHORT).show() }
                                    }
                                )
                            },
                            modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { expandirSugestoes = false; buscarNoBanco(); focusManager.clearFocus() })
                        )
                        DropdownMenu(
                            expanded = expandirSugestoes, onDismissRequest = { expandirSugestoes = false }, modifier = Modifier.fillMaxWidth(0.95f), properties = PopupProperties(focusable = false)
                        ) {
                            sugestoesBusca.forEach { nomeSugestao -> DropdownMenuItem(text = { Text(nomeSugestao) }, onClick = { textoBusca = nomeSugestao; expandirSugestoes = false; buscarNoBanco(); focusManager.clearFocus() }) }
                        }
                    }

                    // LISTA DE PRODUTOS
                    val grupos = todosProdutos.groupBy { if (it.codigoBarras.isNotEmpty()) it.codigoBarras else it.nomeProduto }

                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(grupos.keys.toList()) { chave ->
                            val listaDoGrupo = grupos[chave] ?: emptyList()
                            val melhorOferta = listaDoGrupo.minByOrNull { if (it.valor > 0) it.valor else Double.MAX_VALUE }
                            val totalOfertas = listaDoGrupo.filter { it.valor > 0 }.size

                            if (melhorOferta != null) {
                                Card(modifier = Modifier.padding(8.dp).fillMaxWidth().clickable { carregarDetalhesCompletos(melhorOferta) }, elevation = CardDefaults.cardElevation(4.dp)) {
                                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                        val ofertaComFoto = listaDoGrupo.firstOrNull { it.fotoBase64.isNotEmpty() }
                                        if(ofertaComFoto != null){
                                            val bitmap = stringParaBitmap(ofertaComFoto.fotoBase64)
                                            if(bitmap != null) {
                                                Image(bitmap = bitmap.asImageBitmap(), contentDescription = null, modifier = Modifier.size(50.dp).clip(RoundedCornerShape(4.dp)).background(Color.Gray), contentScale = ContentScale.Crop)
                                                Spacer(modifier = Modifier.width(10.dp))
                                            }
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(text = melhorOferta.nomeProduto, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                            if (melhorOferta.valor > 0) {
                                                Text(text = "Ver $totalOfertas preços", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                                                Text(text = "Atualizado: ${formatarData(melhorOferta.data)}", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                            } else {
                                                Text(text = "Sem ofertas cadastradas", style = MaterialTheme.typography.bodySmall, color = Color.Gray, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                                            }
                                        }
                                        Column(horizontalAlignment = Alignment.End) {
                                            if (melhorOferta.valor > 0) {
                                                Text("R$ ${String.format("%.2f", melhorOferta.valor)}", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                                Text(text = melhorOferta.mercado, style = MaterialTheme.typography.bodyMedium, color = Color(0xFF03A9F4))
                                            } else {
                                                Text("Catálogo", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                                            }

                                            // --- BOTÕES DE AÇÃO ---
                                            Row {
                                                // Se for item do catálogo (sem preço), mostra o LÁPIS
                                                if (melhorOferta.valor == 0.0 && melhorOferta.mercado == "Catálogo Global") {
                                                    IconButton(onClick = {
                                                        // --- CORREÇÃO: Limpa o ID para criar NOVO registro no Firebase ---
                                                        onIrCadastro(melhorOferta.copy(mercado = "", id = ""))
                                                    }, modifier = Modifier.size(30.dp)) {
                                                        Icon(Icons.Default.Edit, "Adicionar Preço", tint = MaterialTheme.colorScheme.primary)
                                                    }
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                }

                                                IconButton(onClick = { adicionarAoCarrinho(melhorOferta) }, modifier = Modifier.size(30.dp)) {
                                                    Icon(Icons.Default.AddShoppingCart, "Adicionar à Lista", tint = MaterialTheme.colorScheme.secondary)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        item { if (temMais && !carregandoMais) { Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { Button(onClick = { carregarProdutos() }) { Text("Carregar mais") } } } }
                    }
                }
                if (carregandoDetalhes) Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
        }
    }

    if (mostrarBannerBoasVindas) AlertDialog(onDismissRequest = onFecharBanner, icon = { Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.primary) }, title = { Text("Bem-vindo!") }, text = { Text("Ajude a comunidade a crescer e economizar adicionando e atualizando preços!", textAlign = TextAlign.Center) }, confirmButton = { Button(onClick = onFecharBanner, modifier = Modifier.fillMaxWidth()) { Text("Entendi, vamos lá!") } })
    if (mostrarSobre) AlertDialog(onDismissRequest = { mostrarSobre = false }, title = { Text("Sobre") }, text = { Text("Versão: 1.0.0 (Beta)") }, confirmButton = { TextButton(onClick = { mostrarSobre = false }) { Text("Fechar") } })
    if (mostrarSuporte) DialogoSuporteUsuario(usuarioLogado = usuarioLogado, onDismiss = { mostrarSuporte = false })

    if (grupoSelecionadoParaDetalhes != null) {
        DialogoRankingDetalhes(
            grupoOfertas = grupoSelecionadoParaDetalhes!!, usuarioLogado = usuarioLogado, isAdmin = isAdmin,
            onDismiss = { grupoSelecionadoParaDetalhes = null }, onIrCadastro = onIrCadastro,
            onDelete = { id ->
                db.collection("ofertas").document(id).delete().addOnSuccessListener { carregarProdutos(resetar = true) }
                grupoSelecionadoParaDetalhes = grupoSelecionadoParaDetalhes!!.filter { it.id != id }
                if(grupoSelecionadoParaDetalhes!!.isEmpty()) grupoSelecionadoParaDetalhes = null
            },
            onUpdate = { nova -> db.collection("ofertas").document(nova.id).set(nova).addOnSuccessListener { carregarProdutos(resetar = true) }; grupoSelecionadoParaDetalhes = null },
            onNovoComentario = { id, txt -> if(!temOfensa(txt)) { val target = grupoSelecionadoParaDetalhes!!.find { it.id == id }; if(target != null) db.collection("ofertas").document(id).update("chatComentarios", target.chatComentarios + "$usuarioLogado: $txt").addOnSuccessListener { carregarProdutos(resetar = true) } } },
            onApagarComentario = { id, txt -> val target = grupoSelecionadoParaDetalhes!!.find { it.id == id }; if(target != null) { val nova = target.chatComentarios.toMutableList().apply { remove(txt) }; db.collection("ofertas").document(id).update("chatComentarios", nova).addOnSuccessListener { carregarProdutos(resetar = true) } } },
            onEditarComentario = { id, old, newTxt -> if(!temOfensa(newTxt)) { val target = grupoSelecionadoParaDetalhes!!.find { it.id == id }; if(target != null) { val nova = target.chatComentarios.toMutableList(); val idx = nova.indexOf(old); if(idx != -1) { val autor = old.split(": ", limit=2)[0]; nova[idx] = "$autor: $newTxt"; db.collection("ofertas").document(id).update("chatComentarios", nova).addOnSuccessListener { carregarProdutos(resetar = true) } } } } }
        )
    }

    if (mostrarDialogoFiltro) DialogoLocalizacao(onDismiss = { mostrarDialogoFiltro = false }, onConfirmar = { est, cid -> onMudarFiltro(est, cid); mostrarDialogoFiltro = false })
}