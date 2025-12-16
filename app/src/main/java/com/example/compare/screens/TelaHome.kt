package com.example.compare.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.compare.model.DadosMercado
import com.example.compare.model.MensagemSuporte
import com.example.compare.model.ProdutoPreco
import com.example.compare.model.Usuario
import com.example.compare.utils.*
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.delay
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
    onSair: () -> Unit
) {
    var textoBusca by remember { mutableStateOf("") }
    val todosProdutos = remember { mutableStateListOf<ProdutoPreco>() }

    var grupoSelecionadoParaDetalhes by remember { mutableStateOf<List<ProdutoPreco>?>(null) }
    var carregandoDetalhes by remember { mutableStateOf(false) }
    var mostrarDialogoFiltro by remember { mutableStateOf(false) }

    var menuExpandido by remember { mutableStateOf(false) }
    var mostrarSobre by remember { mutableStateOf(false) }
    var mostrarSuporte by remember { mutableStateOf(false) }
    var mostrarPainelAdmin by remember { mutableStateOf(false) }

    var ultimoDocumento by remember { mutableStateOf<DocumentSnapshot?>(null) }
    var carregandoMais by remember { mutableStateOf(false) }
    var temMais by remember { mutableStateOf(true) }

    val db = FirebaseFirestore.getInstance()
    val focusManager = LocalFocusManager.current

    // --- MONITOR DE ATIVIDADE (30 SEGUNDOS) ---
    LaunchedEffect(usuarioLogado) {
        if (usuarioLogado != "Administrador" && usuarioLogado != "anonimo" && usuarioLogado.isNotBlank()) {
            while (true) {
                try {
                    db.collection("usuarios").document(usuarioLogado)
                        .update("ultimoAcesso", Date())
                        .addOnFailureListener {
                            db.collection("usuarios").document(usuarioLogado)
                                .set(Usuario(nome = usuarioLogado, ultimoAcesso = Date()))
                        }
                } catch (e: Exception) { }
                delay(30000) // Envia sinal a cada 30 segundos
            }
        }
    }

    var isRefreshing by remember { mutableStateOf(false) }

    fun carregarProdutos(resetar: Boolean = false, onConcluido: () -> Unit = {}) {
        if (carregandoMais) { onConcluido(); return }
        carregandoMais = true

        if (resetar) {
            todosProdutos.clear()
            ultimoDocumento = null
            temMais = true
        }

        var query = db.collection("ofertas")
            .orderBy("data", Query.Direction.DESCENDING)
            .limit(20)

        if (ultimoDocumento != null && !resetar) {
            query = query.startAfter(ultimoDocumento!!)
        }

        query.get().addOnSuccessListener { result ->
            if (!result.isEmpty) {
                if (resetar) ultimoDocumento = result.documents[result.size() - 1]
                for (doc in result) {
                    try {
                        val produto = doc.toObject(ProdutoPreco::class.java).copy(id = doc.id)
                        if (produto.cidade.equals(cidadeAtual, ignoreCase = true) || produto.cidade.isEmpty()) {
                            todosProdutos.add(produto)
                        }
                    } catch (e: Exception) {}
                }
                if (result.size() < 20) temMais = false
            } else {
                temMais = false
            }
            carregandoMais = false
            onConcluido()
        }.addOnFailureListener {
            carregandoMais = false
            onConcluido()
        }
    }

    LaunchedEffect(cidadeAtual) { carregarProdutos(resetar = true) }

    fun buscarNoBanco(onConcluido: () -> Unit = {}) {
        if(textoBusca.isBlank()) { carregarProdutos(resetar = true, onConcluido = onConcluido); return }
        carregandoMais = true
        todosProdutos.clear()

        val termoBusca = textoBusca.lowercase()

        db.collection("ofertas")
            .whereGreaterThanOrEqualTo("nomePesquisa", termoBusca)
            .whereLessThanOrEqualTo("nomePesquisa", termoBusca + "\uf8ff")
            .limit(50)
            .get()
            .addOnSuccessListener { res ->
                for(doc in res) {
                    val p = doc.toObject(ProdutoPreco::class.java).copy(id = doc.id)
                    if (p.cidade.equals(cidadeAtual, ignoreCase = true) || p.cidade.isEmpty()) {
                        todosProdutos.add(p)
                    }
                }
                carregandoMais = false
                temMais = false
                onConcluido()
            }
            .addOnFailureListener {
                carregandoMais = false
                onConcluido()
            }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (textoBusca.isBlank()) {
                    carregarProdutos(resetar = true)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun carregarDetalhesCompletos(produtoBase: ProdutoPreco) {
        carregandoDetalhes = true
        val query = if (produtoBase.codigoBarras.isNotEmpty()) {
            db.collection("ofertas").whereEqualTo("codigoBarras", produtoBase.codigoBarras)
        } else {
            db.collection("ofertas").whereEqualTo("nomeProduto", produtoBase.nomeProduto)
        }

        query.get().addOnSuccessListener { result ->
            val listaCompleta = result.map { doc -> doc.toObject(ProdutoPreco::class.java).copy(id = doc.id) }
            val listaFiltrada = listaCompleta.filter {
                it.cidade.equals(cidadeAtual, ignoreCase = true) || it.cidade.isEmpty()
            }
            grupoSelecionadoParaDetalhes = listaFiltrada
            carregandoDetalhes = false
        }.addOnFailureListener { carregandoDetalhes = false }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Compare - $cidadeAtual", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { mostrarDialogoFiltro = true }) {
                        Icon(Icons.Default.LocationOn, "Local")
                    }
                    IconButton(onClick = { menuExpandido = true }) {
                        Icon(Icons.Default.MoreVert, "Menu")
                    }
                    DropdownMenu(expanded = menuExpandido, onDismissRequest = { menuExpandido = false }) {
                        if(isAdmin) DropdownMenuItem(text = { Text("Painel Admin") }, onClick = { menuExpandido = false; mostrarPainelAdmin = true })
                        DropdownMenuItem(text = { Text("Sobre o App") }, onClick = { menuExpandido = false; mostrarSobre = true })
                        DropdownMenuItem(text = { Text("Suporte") }, onClick = { menuExpandido = false; mostrarSuporte = true })
                        Divider()
                        DropdownMenuItem(text = { Text("Sair", color = Color.Red) }, onClick = { menuExpandido = false; onSair() })
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onIrCadastro(null) }) { Icon(Icons.Default.Add, "Adicionar") }
        }
    ) { padding ->
        PullToRefreshBox(
            modifier = Modifier.padding(padding),
            isRefreshing = isRefreshing,
            onRefresh = {
                isRefreshing = true
                if (textoBusca.isNotBlank()) buscarNoBanco { isRefreshing = false }
                else carregarProdutos(resetar = true) { isRefreshing = false }
            }
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Column {
                    OutlinedTextField(
                        value = textoBusca,
                        onValueChange = { textoBusca = it },
                        label = { Text("Buscar produto...") },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { buscarNoBanco(); focusManager.clearFocus() })
                    )

                    val grupos = todosProdutos.groupBy { if (it.codigoBarras.isNotEmpty()) it.codigoBarras else it.nomeProduto }

                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(grupos.keys.toList()) { chave ->
                            val listaDoGrupo = grupos[chave] ?: emptyList()
                            val melhorOferta = listaDoGrupo.minByOrNull { it.valor }
                            val totalOfertas = listaDoGrupo.size

                            if (melhorOferta != null) {
                                Card(
                                    modifier = Modifier
                                        .padding(8.dp)
                                        .fillMaxWidth()
                                        .clickable { carregarDetalhesCompletos(melhorOferta) },
                                    elevation = CardDefaults.cardElevation(4.dp)
                                ) {
                                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                        val ofertaComFoto = listaDoGrupo.firstOrNull { it.fotoBase64.isNotEmpty() }
                                        if(ofertaComFoto != null){
                                            val bitmap = stringParaBitmap(ofertaComFoto.fotoBase64)
                                            if(bitmap != null) {
                                                Image(
                                                    bitmap = bitmap.asImageBitmap(),
                                                    contentDescription = null,
                                                    modifier = Modifier.size(50.dp).clip(RoundedCornerShape(4.dp)).background(Color.Gray),
                                                    contentScale = ContentScale.Crop
                                                )
                                                Spacer(modifier = Modifier.width(10.dp))
                                            }
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(text = melhorOferta.nomeProduto, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                            Text(text = "Ver $totalOfertas preços", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                                            Text(text = "Atualizado: ${formatarData(melhorOferta.data)}", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                        }
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text("R$ ${String.format("%.2f", melhorOferta.valor)}", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                            Text(text = melhorOferta.mercado, style = MaterialTheme.typography.bodyMedium, color = Color(0xFF03A9F4))
                                        }
                                    }
                                }
                            }
                        }
                        item {
                            if (temMais) {
                                Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                    if (carregandoMais) CircularProgressIndicator() else Button(onClick = { carregarProdutos() }) { Text("Carregar mais") }
                                }
                            }
                        }
                    }
                }

                if (carregandoDetalhes) {
                    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
            }
        }
    }

    if (mostrarBannerBoasVindas) {
        AlertDialog(onDismissRequest = onFecharBanner, icon = { Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.primary) }, title = { Text("Bem-vindo!") }, text = { Text("Ajude a comunidade a crescer e economizar adicionando e atualizando preços!", textAlign = TextAlign.Center) }, confirmButton = { Button(onClick = onFecharBanner, modifier = Modifier.fillMaxWidth()) { Text("Entendi, vamos lá!") } })
    }
    if (mostrarSobre) AlertDialog(onDismissRequest = { mostrarSobre = false }, title = { Text("Sobre") }, text = { Text("Versão: 1.0.0 (Beta)") }, confirmButton = { TextButton(onClick = { mostrarSobre = false }) { Text("Fechar") } })

    if (mostrarSuporte) {
        DialogoSuporteUsuario(usuarioLogado = usuarioLogado, onDismiss = { mostrarSuporte = false })
    }

    if (mostrarPainelAdmin) DialogoAdmin(onDismiss = { mostrarPainelAdmin = false })

    if (grupoSelecionadoParaDetalhes != null) {
        DialogoRankingDetalhes(
            grupoOfertas = grupoSelecionadoParaDetalhes!!, usuarioLogado = usuarioLogado, isAdmin = isAdmin,
            onDismiss = { grupoSelecionadoParaDetalhes = null }, onIrCadastro = onIrCadastro,
            onDelete = { id ->
                db.collection("ofertas").document(id).delete().addOnSuccessListener { carregarProdutos(resetar = true) }
                grupoSelecionadoParaDetalhes = grupoSelecionadoParaDetalhes!!.filter { it.id != id }
                if(grupoSelecionadoParaDetalhes!!.isEmpty()) grupoSelecionadoParaDetalhes = null
            },
            onUpdate = { nova ->
                db.collection("ofertas").document(nova.id).set(nova).addOnSuccessListener { carregarProdutos(resetar = true) }
                grupoSelecionadoParaDetalhes = null
            },
            onNovoComentario = { id, txt ->
                if(!temOfensa(txt)) {
                    val target = grupoSelecionadoParaDetalhes!!.find { it.id == id }
                    if(target != null) db.collection("ofertas").document(id).update("chatComentarios", target.chatComentarios + "$usuarioLogado: $txt").addOnSuccessListener { carregarProdutos(resetar = true) }
                }
            },
            onApagarComentario = { id, txt ->
                val target = grupoSelecionadoParaDetalhes!!.find { it.id == id }
                if(target != null) {
                    val nova = target.chatComentarios.toMutableList().apply { remove(txt) }
                    db.collection("ofertas").document(id).update("chatComentarios", nova).addOnSuccessListener { carregarProdutos(resetar = true) }
                }
            },
            onEditarComentario = { id, old, newTxt ->
                if(!temOfensa(newTxt)) {
                    val target = grupoSelecionadoParaDetalhes!!.find { it.id == id }
                    if(target != null) {
                        val nova = target.chatComentarios.toMutableList()
                        val idx = nova.indexOf(old)
                        if(idx != -1) {
                            val autor = old.split(": ", limit=2)[0]
                            nova[idx] = "$autor: $newTxt"
                            db.collection("ofertas").document(id).update("chatComentarios", nova).addOnSuccessListener { carregarProdutos(resetar = true) }
                        }
                    }
                }
            }
        )
    }

    if (mostrarDialogoFiltro) {
        DialogoLocalizacao(onDismiss = { mostrarDialogoFiltro = false }, onConfirmar = { est, cid -> onMudarFiltro(est, cid); mostrarDialogoFiltro = false })
    }
}

// --- FUNÇÃO QUE FALTAVA (DialogoSuporteUsuario) ---
@Composable
fun DialogoSuporteUsuario(usuarioLogado: String, onDismiss: () -> Unit) {
    val db = FirebaseFirestore.getInstance()
    var msg by remember { mutableStateOf("") }
    var historico by remember { mutableStateOf(emptyList<MensagemSuporte>()) }
    var carregando by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        db.collection("suporte")
            .whereEqualTo("usuario", usuarioLogado)
            .addSnapshotListener { result, _ ->
                if (result != null) {
                    historico = result.documents.map { doc -> doc.toObject(MensagemSuporte::class.java)!!.copy(id = doc.id) }.sortedBy { it.data }
                    carregando = false
                }
            }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Suporte e Dúvidas") },
        text = {
            Column(modifier = Modifier.fillMaxWidth().height(400.dp)) {
                if (carregando) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                } else {
                    LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        if(historico.isEmpty()) {
                            item { Text("Envie sua dúvida abaixo.", color = Color.Gray, modifier = Modifier.padding(8.dp)) }
                        }
                        items(historico) { item ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFEEEEEE)),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text("Você: ${item.msg}", fontWeight = FontWeight.Bold, color = Color.Black)
                                    if(item.resposta.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text("Admin: ${item.resposta}", color = Color(0xFF006400), fontWeight = FontWeight.Bold)
                                        Text(formatarData(item.dataResposta ?: Date()), fontSize = 10.sp, color = Color.Gray)
                                    } else {
                                        Text("Aguardando resposta...", fontSize = 10.sp, color = Color.Red)
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = msg,
                        onValueChange = { msg = it },
                        label = { Text("Nova mensagem") },
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            IconButton(onClick = {
                                if(msg.isNotBlank()) {
                                    val nova = MensagemSuporte(usuario = usuarioLogado, msg = msg, data = Date())
                                    db.collection("suporte").add(nova)
                                    msg = ""
                                }
                            }) { Icon(Icons.Default.Send, null, tint = MaterialTheme.colorScheme.primary) }
                        }
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fechar") } }
    )
}

// --- PAINEL ADMIN ATUALIZADO (ONLINE + HISTÓRICO) ---
@Composable
fun DialogoAdmin(onDismiss: () -> Unit) {
    var aba by remember { mutableStateOf(0) }
    var novoBanimento by remember { mutableStateOf("") }
    val db = FirebaseFirestore.getInstance()

    var listaUsuarios by remember { mutableStateOf(emptyList<Usuario>()) }
    var listaSuporte by remember { mutableStateOf(emptyList<MensagemSuporte>()) }
    var carregando by remember { mutableStateOf(false) }

    var mensagemParaResponder by remember { mutableStateOf<MensagemSuporte?>(null) }
    var textoResposta by remember { mutableStateOf("") }

    LaunchedEffect(aba, mensagemParaResponder) {
        carregando = true
        if (aba == 0 || aba == 1) { // Usuários
            db.collection("usuarios").get().addOnSuccessListener { result ->
                listaUsuarios = result.toObjects(Usuario::class.java).sortedByDescending { it.ultimoAcesso }
                carregando = false
            }
        } else if (aba == 2) { // Suporte
            db.collection("suporte").orderBy("data", Query.Direction.DESCENDING).get().addOnSuccessListener { result ->
                listaSuporte = result.documents.map { doc -> doc.toObject(MensagemSuporte::class.java)!!.copy(id = doc.id) }
                carregando = false
            }
        } else {
            carregando = false
        }
    }

    // Filtra usuários online (acesso nos últimos 60s)
    val tempoAgora = Date().time
    val usuariosOnline = listaUsuarios.filter { (tempoAgora - it.ultimoAcesso.time) < 60000 }

    if (mensagemParaResponder != null) {
        AlertDialog(
            onDismissRequest = { mensagemParaResponder = null },
            title = { Text("Responder ${mensagemParaResponder!!.usuario}") },
            text = { Column { Text("Dúvida: ${mensagemParaResponder!!.msg}", fontStyle = androidx.compose.ui.text.font.FontStyle.Italic); Spacer(modifier = Modifier.height(10.dp)); OutlinedTextField(value = textoResposta, onValueChange = { textoResposta = it }, label = { Text("Sua Resposta") }, modifier = Modifier.fillMaxWidth()) } },
            confirmButton = { Button(onClick = { if (textoResposta.isNotBlank()) { db.collection("suporte").document(mensagemParaResponder!!.id).update(mapOf("resposta" to textoResposta, "dataResposta" to Date())); mensagemParaResponder = null; textoResposta = "" } }) { Text("Enviar Resposta") } },
            dismissButton = { TextButton(onClick = { mensagemParaResponder = null }) { Text("Cancelar") } }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Painel Admin") },
        text = {
            Column(modifier = Modifier.height(400.dp)) {
                ScrollableTabRow(selectedTabIndex = aba, edgePadding = 0.dp) {
                    Tab(selected = aba == 0, onClick = { aba = 0 }, text = { Text("Online") })
                    Tab(selected = aba == 1, onClick = { aba = 1 }, text = { Text("Histórico") })
                    Tab(selected = aba == 2, onClick = { aba = 2 }, text = { Text("Suporte") })
                    Tab(selected = aba == 3, onClick = { aba = 3 }, text = { Text("Banir") })
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (carregando) {
                    Box(modifier = Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                } else {
                    when(aba) {
                        0 -> { // ONLINE (Verde)
                            Text("Online Agora: ${usuariosOnline.size}", fontWeight = FontWeight.Bold, color = Color(0xFF00C853), modifier = Modifier.padding(bottom = 8.dp))
                            LazyColumn {
                                items(usuariosOnline) { user ->
                                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF252525))) {
                                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color(0xFF00C853))) // Bolinha Verde
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column {
                                                Text(user.nome, fontWeight = FontWeight.Bold, color = Color.White)
                                                Text("Visto agora", fontSize = 10.sp, color = Color.LightGray)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        1 -> { // HISTÓRICO (Cinza)
                            Text("Total Registrados: ${listaUsuarios.size}", fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                            LazyColumn {
                                items(listaUsuarios) { user ->
                                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF252525))) {
                                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color.Gray)) // Bolinha Cinza
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column {
                                                Text(user.nome, fontWeight = FontWeight.Bold, color = Color.White)
                                                Text("Último acesso: ${formatarData(user.ultimoAcesso)}", fontSize = 10.sp, color = Color.Gray)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        2 -> { // SUPORTE
                            if(listaSuporte.isEmpty()) Text("Nenhuma mensagem.")
                            LazyColumn {
                                items(listaSuporte) { msg ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { mensagemParaResponder = msg },
                                        colors = CardDefaults.cardColors(containerColor = if(msg.resposta.isEmpty()) Color(0xFF4A4A4A) else Color(0xFF2E7D32))
                                    ) {
                                        Column(modifier = Modifier.padding(8.dp)) {
                                            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                                Text(msg.usuario, fontWeight = FontWeight.Bold, color = Color.Cyan)
                                                Text(formatarData(msg.data), fontSize = 10.sp, color = Color.LightGray)
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(msg.msg, color = Color.White)
                                            if (msg.resposta.isNotEmpty()) {
                                                Divider(color = Color.Gray, modifier = Modifier.padding(vertical = 4.dp))
                                                Text("Resp: ${msg.resposta}", color = Color.Yellow, fontSize = 12.sp)
                                            } else {
                                                Text("Toque para responder", fontSize = 10.sp, color = Color.LightGray, modifier = Modifier.padding(top = 4.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        3 -> { // BANIR
                            OutlinedTextField(value = novoBanimento, onValueChange = { novoBanimento = it }, label = { Text("Usuário para banir") }, modifier = Modifier.fillMaxWidth())
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(onClick = { if(novoBanimento.isNotBlank()) { db.collection("usuarios_banidos").document(novoBanimento.lowercase()).set(hashMapOf("data" to Date())); novoBanimento = "" } }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) { Text("BANIR") }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Nota: Banir impede o login futuro deste nome.", fontSize = 12.sp, color = Color.Gray)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fechar") } }
    )
}

@Composable
fun DialogoRankingDetalhes(grupoOfertas: List<ProdutoPreco>, usuarioLogado: String, isAdmin: Boolean, onDismiss: () -> Unit, onIrCadastro: (ProdutoPreco) -> Unit, onDelete: (String) -> Unit, onUpdate: (ProdutoPreco) -> Unit, onNovoComentario: (String, String) -> Unit, onApagarComentario: (String, String) -> Unit, onEditarComentario: (String, String, String) -> Unit) {
    val listaOrdenada = grupoOfertas.sortedBy { it.valor }
    val produtoBase = listaOrdenada.first()
    val todosComentarios = grupoOfertas.flatMap { oferta -> oferta.chatComentarios.map { comentario -> Pair(oferta.id, comentario) } }
    var ofertaEmEdicao by remember { mutableStateOf<ProdutoPreco?>(null) }
    var comentarioEmEdicao by remember { mutableStateOf<Pair<String, String>?>(null) }
    var mercadoSelecionado by remember { mutableStateOf<String?>(null) }
    var textoChat by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current

    if (mercadoSelecionado != null) { DialogoDadosMercado(nomeMercado = mercadoSelecionado!!, isAdmin = isAdmin, onDismiss = { mercadoSelecionado = null }) }
    else if (ofertaEmEdicao != null) { TelaEdicaoInterna(oferta = ofertaEmEdicao!!, onCancel = { ofertaEmEdicao = null }, onSave = { novaOferta -> onUpdate(novaOferta); ofertaEmEdicao = null }) }
    else if (comentarioEmEdicao != null) {
        var textoEditado by remember { mutableStateOf(comentarioEmEdicao!!.second.split(": ", limit = 2).getOrElse(1) { "" }) }
        AlertDialog(onDismissRequest = { comentarioEmEdicao = null }, title = { Text("Editar Comentário") }, text = { OutlinedTextField(value = textoEditado, onValueChange = { textoEditado = it }, modifier = Modifier.fillMaxWidth()) }, confirmButton = { Button(onClick = { if (textoEditado.isNotBlank()) { onEditarComentario(comentarioEmEdicao!!.first, comentarioEmEdicao!!.second, textoEditado); comentarioEmEdicao = null } }) { Text("Salvar") } }, dismissButton = { TextButton(onClick = { comentarioEmEdicao = null }) { Text("Cancelar") } })
    } else {
        AlertDialog(onDismissRequest = onDismiss, title = { Text(produtoBase.nomeProduto, fontWeight = FontWeight.Bold) }, text = {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                val ofertaComFoto = listaOrdenada.firstOrNull { it.fotoBase64.isNotEmpty() }
                if (ofertaComFoto != null) { item { val bitmap = stringParaBitmap(ofertaComFoto.fotoBase64); if (bitmap != null) { Box(modifier = Modifier.fillMaxWidth().height(150.dp).padding(bottom = 10.dp), contentAlignment = Alignment.Center) { Image(bitmap = bitmap.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit) } } } }
                items(listaOrdenada) { oferta ->
                    Column {
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = oferta.mercado, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF03A9F4), modifier = Modifier.clickable { mercadoSelecionado = oferta.mercado })
                                Text("Por: ${oferta.usuarioId} • ${formatarData(oferta.data)}", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                if (oferta.comentario.isNotEmpty()) Text("Obs: ${oferta.comentario}", style = MaterialTheme.typography.bodySmall, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("R$ ${String.format("%.2f", oferta.valor)}", color = if (oferta == listaOrdenada.first()) Color(0xFF006400) else Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                Row { IconButton(onClick = { ofertaEmEdicao = oferta }) { Icon(Icons.Default.Edit, "Editar", modifier = Modifier.size(20.dp), tint = Color.Blue) }; if (isAdmin) { IconButton(onClick = { onDelete(oferta.id) }) { Icon(Icons.Default.Delete, "Excluir", modifier = Modifier.size(20.dp), tint = Color.Red) } } }
                            }
                        }
                        Divider(modifier = Modifier.padding(vertical = 4.dp))
                    }
                }
                item {
                    Spacer(modifier = Modifier.height(16.dp)); Text("Comentários", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary); Spacer(modifier = Modifier.height(8.dp))
                    if (todosComentarios.isEmpty()) { Text("Seja o primeiro a comentar!", fontSize = 12.sp, color = Color.Gray) }
                    else { todosComentarios.forEach { (idOferta, msg) -> val isOwner = msg.startsWith("$usuarioLogado: "); Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF333333)), modifier = Modifier.padding(vertical = 2.dp).fillMaxWidth()) { Column(modifier = Modifier.padding(8.dp)) { Text(msg, fontSize = 13.sp); if (isOwner || isAdmin) { Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { if (isOwner) { IconButton(onClick = { comentarioEmEdicao = Pair(idOferta, msg) }, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.Edit, "Editar", tint = Color.Gray) } }; IconButton(onClick = { onApagarComentario(idOferta, msg) }, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.Delete, "Apagar", tint = Color.Red) } } } } } } }
                    Spacer(modifier = Modifier.height(8.dp)); Row(verticalAlignment = Alignment.CenterVertically) { OutlinedTextField(value = textoChat, onValueChange = { textoChat = it }, placeholder = { Text("Escreva um comentário...", fontSize = 12.sp) }, modifier = Modifier.weight(1f).height(56.dp), singleLine = true, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send), keyboardActions = KeyboardActions(onSend = { if (textoChat.isNotBlank()) { onNovoComentario(produtoBase.id, textoChat); textoChat = ""; focusManager.clearFocus() } })); IconButton(onClick = { if (textoChat.isNotBlank()) { onNovoComentario(produtoBase.id, textoChat); textoChat = ""; focusManager.clearFocus() } }) { Icon(Icons.Default.Send, null, tint = MaterialTheme.colorScheme.primary) } }
                }
            }
        }, confirmButton = { Button(onClick = { onDismiss(); onIrCadastro(produtoBase) }) { Text("Adicionar Outro Mercado") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Fechar") } })
    }
}

@Composable
fun DialogoDadosMercado(nomeMercado: String, isAdmin: Boolean, onDismiss: () -> Unit) {
    val db = FirebaseFirestore.getInstance()
    var endereco by remember { mutableStateOf("") }
    var telefone by remember { mutableStateOf("") }
    var horario by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var isEditing by remember { mutableStateOf(false) }
    LaunchedEffect(nomeMercado) { db.collection("mercados").document(nomeMercado).get().addOnSuccessListener { doc -> if (doc.exists()) { endereco = doc.getString("endereco") ?: ""; telefone = doc.getString("telefone") ?: ""; horario = doc.getString("horario") ?: "" }; loading = false } }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(nomeMercado) }, text = { if (loading) CircularProgressIndicator() else Column { if (isEditing) { OutlinedTextField(value = endereco, onValueChange = { endereco = it }, label = { Text("Endereço") }); OutlinedTextField(value = telefone, onValueChange = { telefone = it }, label = { Text("Telefone") }); OutlinedTextField(value = horario, onValueChange = { horario = it }, label = { Text("Horário") }) } else { Row { Icon(Icons.Default.LocationOn, null); Spacer(Modifier.width(8.dp)); Text(if(endereco.isEmpty()) "Sem endereço" else endereco) }; Spacer(Modifier.height(8.dp)); Row { Icon(Icons.Default.Phone, null); Spacer(Modifier.width(8.dp)); Text(if(telefone.isEmpty()) "Sem telefone" else telefone) }; Spacer(Modifier.height(8.dp)); Row { Icon(Icons.Default.Schedule, null); Spacer(Modifier.width(8.dp)); Text(if(horario.isEmpty()) "Sem horário" else horario) } } } }, confirmButton = { if (isAdmin) { Button(onClick = { if (isEditing) { db.collection("mercados").document(nomeMercado).set(DadosMercado(nomeMercado, nomeMercado, endereco, telefone, horario)); isEditing = false } else isEditing = true }) { Text(if (isEditing) "Salvar" else "Editar Dados") } } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Fechar") } })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DialogoLocalizacao(onDismiss: () -> Unit, onConfirmar: (String, String) -> Unit) {
    var estadoSel by remember { mutableStateOf("SC") }
    var cidadeSel by remember { mutableStateOf("") }
    var buscaCidade by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Selecionar Local") }, text = { Column(modifier = Modifier.fillMaxWidth()) { Text(text = "Estado:", fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp)); LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) { items(dadosBrasil.keys.sorted()) { uf -> FilterChip(selected = estadoSel == uf, onClick = { estadoSel = uf; buscaCidade = "" }, label = { Text(uf) }) } }; Spacer(modifier = Modifier.height(16.dp)); OutlinedTextField(value = buscaCidade, onValueChange = { buscaCidade = it }, label = { Text("Buscar cidade...") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }), trailingIcon = { if (buscaCidade.isNotEmpty()) { IconButton(onClick = { buscaCidade = "" }) { Icon(Icons.Default.Close, "Limpar") } } else { Icon(Icons.Default.Search, null) } }); Spacer(modifier = Modifier.height(8.dp)); val cidadesFiltradas = dadosBrasil[estadoSel]?.filter { it.contains(buscaCidade, ignoreCase = true) } ?: emptyList(); LazyColumn(modifier = Modifier.height(200.dp)) { items(cidadesFiltradas) { cidade -> Row(modifier = Modifier.fillMaxWidth().clickable { cidadeSel = cidade }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) { RadioButton(selected = (cidadeSel == cidade), onClick = null); Text(text = cidade, modifier = Modifier.padding(start = 8.dp)) }; Divider(color = Color.LightGray, thickness = 0.5.dp) } } } }, confirmButton = { Button(onClick = { if (cidadeSel.isNotEmpty()) { onConfirmar(estadoSel, cidadeSel) } else { val primeiraCidade = dadosBrasil[estadoSel]?.firstOrNull() ?: ""; onConfirmar(estadoSel, primeiraCidade) } }) { Text("Confirmar") } })
}