package com.example.compare.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.compare.model.DadosMercado
import com.example.compare.model.MensagemSuporte
import com.example.compare.model.ProdutoPreco
import com.example.compare.utils.*
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Date

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

@Composable
fun DialogoRankingDetalhes(
    grupoOfertas: List<ProdutoPreco>,
    usuarioLogado: String,
    isAdmin: Boolean,
    onDismiss: () -> Unit,
    onIrCadastro: (ProdutoPreco) -> Unit,
    onDelete: (String) -> Unit,
    onUpdate: (ProdutoPreco) -> Unit,
    onNovoComentario: (String, String) -> Unit,
    onApagarComentario: (String, String) -> Unit,
    onEditarComentario: (String, String, String) -> Unit,
    onAtualizarNomeProduto: (String) -> Unit,
    onAtualizarNomeMercado: (String, String) -> Unit,
    onApagarMercado: (String) -> Unit,
    onApagarProdutoGlobal: (ProdutoPreco) -> Unit,
    onAtualizarCodigoBarras: (String) -> Unit // --- NOVO: Callback para atualizar código ---
) {
    val listaOrdenada = grupoOfertas.sortedBy { it.valor }
    val produtoBase = listaOrdenada.first()
    val todosComentarios = grupoOfertas.flatMap { oferta -> oferta.chatComentarios.map { comentario -> Pair(oferta.id, comentario) } }

    // Estados dos Diálogos Internos
    var ofertaEmEdicao by remember { mutableStateOf<ProdutoPreco?>(null) }
    var comentarioEmEdicao by remember { mutableStateOf<Pair<String, String>?>(null) }
    var mercadoSelecionado by remember { mutableStateOf<String?>(null) }

    // Estados Admin
    var editandoNomeProduto by remember { mutableStateOf(false) }
    var novoNomeProduto by remember { mutableStateOf(produtoBase.nomeProduto) }

    // --- NOVO: Estado para editar código de barras ---
    var editandoCodigoBarras by remember { mutableStateOf(false) }
    var novoCodigoBarras by remember { mutableStateOf(produtoBase.codigoBarras) }

    var mercadoParaEditar by remember { mutableStateOf<String?>(null) }

    var textoChat by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current

    var mostrarConfirmacaoApagarProduto by remember { mutableStateOf(false) }

    // 1. Detalhes do Mercado
    if (mercadoSelecionado != null) {
        DialogoDadosMercado(nomeMercado = mercadoSelecionado!!, isAdmin = isAdmin, onDismiss = { mercadoSelecionado = null })
    }
    // 2. Editar Preço
    else if (ofertaEmEdicao != null) {
        DialogoEditarOferta(
            oferta = ofertaEmEdicao!!,
            onCancel = { ofertaEmEdicao = null },
            onSave = { novaOferta: ProdutoPreco -> onUpdate(novaOferta); ofertaEmEdicao = null }
        )
    }
    // 3. Editar Comentário
    else if (comentarioEmEdicao != null) {
        var textoEditado by remember { mutableStateOf(comentarioEmEdicao!!.second.split(": ", limit = 2).getOrElse(1) { "" }) }
        AlertDialog(onDismissRequest = { comentarioEmEdicao = null }, title = { Text("Editar Comentário") }, text = { OutlinedTextField(value = textoEditado, onValueChange = { textoEditado = it }, modifier = Modifier.fillMaxWidth()) }, confirmButton = { Button(onClick = { if (textoEditado.isNotBlank()) { onEditarComentario(comentarioEmEdicao!!.first, comentarioEmEdicao!!.second, textoEditado); comentarioEmEdicao = null } }) { Text("Salvar") } }, dismissButton = { TextButton(onClick = { comentarioEmEdicao = null }) { Text("Cancelar") } })
    }
    // 4. Renomear Produto
    else if (editandoNomeProduto) {
        AlertDialog(
            onDismissRequest = { editandoNomeProduto = false },
            title = { Text("Renomear Produto") },
            text = {
                Column {
                    Text("Isso alterará o nome em TODAS as ofertas deste produto.", fontSize = 12.sp, color = Color.Red)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = novoNomeProduto, onValueChange = { novoNomeProduto = it }, label = { Text("Novo Nome") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = { Button(onClick = { if (novoNomeProduto.isNotBlank()) { onAtualizarNomeProduto(novoNomeProduto); editandoNomeProduto = false } }) { Text("Salvar") } },
            dismissButton = { TextButton(onClick = { editandoNomeProduto = false }) { Text("Cancelar") } }
        )
    }
    // --- NOVO: Editar Código de Barras ---
    else if (editandoCodigoBarras) {
        AlertDialog(
            onDismissRequest = { editandoCodigoBarras = false },
            title = { Text("Editar Código de Barras") },
            text = {
                Column {
                    Text("Corrija o código se estiver errado. Isso afeta todas as ofertas.", fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = novoCodigoBarras,
                        onValueChange = { if(it.all { c -> c.isDigit() }) novoCodigoBarras = it },
                        label = { Text("Novo Código") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (novoCodigoBarras.isNotBlank()) {
                        onAtualizarCodigoBarras(novoCodigoBarras)
                        editandoCodigoBarras = false
                    }
                }) { Text("Salvar") }
            },
            dismissButton = { TextButton(onClick = { editandoCodigoBarras = false }) { Text("Cancelar") } }
        )
    }
    // 5. Renomear/Apagar Mercado
    else if (mercadoParaEditar != null) {
        var novoNomeMercado by remember { mutableStateOf(mercadoParaEditar!!) }
        var confirmandoApagarMercado by remember { mutableStateOf(false) }

        if(confirmandoApagarMercado){
            AlertDialog(
                onDismissRequest = { confirmandoApagarMercado = false },
                title = { Text("Excluir Mercado?", color = Color.Red) },
                text = { Text("Isso apagará o cadastro do mercado '$mercadoParaEditar'. As ofertas permanecerão, mas sem vínculo com o endereço.") },
                confirmButton = {
                    Button(
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                        onClick = {
                            onApagarMercado(mercadoParaEditar!!)
                            mercadoParaEditar = null
                            confirmandoApagarMercado = false
                        }
                    ) { Text("Excluir Definitivamente") }
                },
                dismissButton = { TextButton(onClick = { confirmandoApagarMercado = false }) { Text("Voltar") } }
            )
        } else {
            AlertDialog(
                onDismissRequest = { mercadoParaEditar = null },
                title = { Text("Gerenciar Mercado") },
                text = {
                    Column {
                        Text("Edite o nome ou exclua o mercado do sistema.", fontSize = 12.sp)
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(value = novoNomeMercado, onValueChange = { novoNomeMercado = it }, label = { Text("Nome do Mercado") }, modifier = Modifier.fillMaxWidth())
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        if (novoNomeMercado.isNotBlank() && novoNomeMercado != mercadoParaEditar) {
                            onAtualizarNomeMercado(mercadoParaEditar!!, novoNomeMercado)
                            mercadoParaEditar = null
                        } else {
                            mercadoParaEditar = null
                        }
                    }) { Text("Salvar Alteração") }
                },
                dismissButton = {
                    Row {
                        TextButton(onClick = { confirmandoApagarMercado = true }, colors = ButtonDefaults.textButtonColors(contentColor = Color.Red)) { Text("Excluir Mercado") }
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = { mercadoParaEditar = null }) { Text("Cancelar") }
                    }
                }
            )
        }
    }
    // 6. Confirmar Apagar Produto
    else if (mostrarConfirmacaoApagarProduto) {
        AlertDialog(
            onDismissRequest = { mostrarConfirmacaoApagarProduto = false },
            title = { Text("Excluir Produto?", color = Color.Red) },
            text = { Text("ATENÇÃO: Isso excluirá o produto do catálogo e TODAS as ofertas associadas a ele. Esta ação não pode ser desfeita.") },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                    onClick = {
                        onApagarProdutoGlobal(produtoBase)
                        mostrarConfirmacaoApagarProduto = false
                    }
                ) { Text("Sim, Excluir Tudo") }
            },
            dismissButton = { TextButton(onClick = { mostrarConfirmacaoApagarProduto = false }) { Text("Cancelar") } }
        )
    }
    // 7. Lista de Ofertas (PRINCIPAL)
    else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Column {
                    // Linha do Nome
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(produtoBase.nomeProduto, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        if (isAdmin) {
                            IconButton(onClick = { novoNomeProduto = produtoBase.nomeProduto; editandoNomeProduto = true }) {
                                Icon(Icons.Default.Edit, "Editar Nome", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            }
                            IconButton(onClick = { mostrarConfirmacaoApagarProduto = true }) {
                                Icon(Icons.Default.DeleteForever, "Apagar Produto", tint = Color.Red, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                    // --- NOVO: Linha do Código de Barras ---
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (produtoBase.codigoBarras.isNotEmpty()) "Cód: ${produtoBase.codigoBarras}" else "Sem código de barras",
                            fontSize = 12.sp,
                            color = Color.Gray,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                        if (isAdmin) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Editar Código",
                                tint = Color.Gray,
                                modifier = Modifier.size(14.dp).clickable {
                                    novoCodigoBarras = produtoBase.codigoBarras
                                    editandoCodigoBarras = true
                                }
                            )
                        }
                    }
                }
            },
            text = {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    val ofertaComFoto = listaOrdenada.firstOrNull { it.fotoBase64.isNotEmpty() }
                    if (ofertaComFoto != null) { item { val bitmap = stringParaBitmap(ofertaComFoto.fotoBase64); if (bitmap != null) { Box(modifier = Modifier.fillMaxWidth().height(150.dp).padding(bottom = 10.dp), contentAlignment = Alignment.Center) { Image(bitmap = bitmap.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit) } } } }

                    items(listaOrdenada, key = { it.id }) { oferta ->
                        Column {
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = oferta.mercado,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp,
                                            color = Color(0xFF03A9F4),
                                            modifier = Modifier.clickable { mercadoSelecionado = oferta.mercado }
                                        )
                                        if (isAdmin) {
                                            Spacer(Modifier.width(8.dp))
                                            IconButton(
                                                onClick = { mercadoParaEditar = oferta.mercado },
                                                modifier = Modifier.size(18.dp)
                                            ) {
                                                Icon(Icons.Default.Edit, "Editar Mercado", tint = Color.Gray)
                                            }
                                        }
                                    }
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
            },
            confirmButton = {
                Button(onClick = {
                    onDismiss()
                    onIrCadastro(produtoBase.copy(id = "", mercado = "", valor = 0.0, usuarioId = ""))
                }) { Text("Adicionar Outro Mercado") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Fechar") } })
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

@Composable
fun DialogoEditarOferta(oferta: ProdutoPreco, onCancel: () -> Unit, onSave: (ProdutoPreco) -> Unit) {
    var valor by remember { mutableStateOf(oferta.valor.toString()) }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Editar Preço") },
        text = { OutlinedTextField(value = valor, onValueChange = { valor = it }, label = { Text("Novo Valor") }) },
        confirmButton = { Button(onClick = { onSave(oferta.copy(valor = valor.toDoubleOrNull() ?: 0.0)) }) { Text("Salvar") } }
    )
}