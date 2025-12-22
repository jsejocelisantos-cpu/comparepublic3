package com.example.compare.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.compare.model.MensagemSuporte
import com.example.compare.model.Usuario
import com.example.compare.utils.formatarData
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelaAdmin(onVoltar: () -> Unit) {
    var aba by remember { mutableStateOf(0) }
    var novoBanimento by remember { mutableStateOf("") }
    val db = FirebaseFirestore.getInstance()
    val context = LocalContext.current // Necessário para ler o CSV

    var listaUsuarios by remember { mutableStateOf(emptyList<Usuario>()) }
    var listaSuporte by remember { mutableStateOf(emptyList<MensagemSuporte>()) }
    var carregando by remember { mutableStateOf(false) }

    var mensagemParaResponder by remember { mutableStateOf<MensagemSuporte?>(null) }
    var textoResposta by remember { mutableStateOf("") }

    // Carrega dados conforme a aba selecionada
    LaunchedEffect(aba, mensagemParaResponder) {
        if (aba == 0 || aba == 1) { // Usuários
            carregando = true
            db.collection("usuarios").get().addOnSuccessListener { result ->
                listaUsuarios = result.toObjects(Usuario::class.java).sortedByDescending { it.ultimoAcesso }
                carregando = false
            }
        } else if (aba == 2) { // Suporte
            carregando = true
            db.collection("suporte").orderBy("data", Query.Direction.DESCENDING).get().addOnSuccessListener { result ->
                listaSuporte = result.documents.map { doc -> doc.toObject(MensagemSuporte::class.java)!!.copy(id = doc.id) }
                carregando = false
            }
        } else {
            // Abas Banir (3) e Ferramentas (4) não precisam carregar listas iniciais
            carregando = false
        }
    }

    val tempoAgora = Date().time
    val usuariosOnline = listaUsuarios.filter { (tempoAgora - it.ultimoAcesso.time) < 60000 } // Considera online se visto nos últimos 60s

    // Diálogo interno para responder suporte
    if (mensagemParaResponder != null) {
        AlertDialog(
            onDismissRequest = { mensagemParaResponder = null },
            title = { Text("Responder ${mensagemParaResponder!!.usuario}") },
            text = { Column { Text("Dúvida: ${mensagemParaResponder!!.msg}", fontStyle = FontStyle.Italic); Spacer(modifier = Modifier.height(10.dp)); OutlinedTextField(value = textoResposta, onValueChange = { textoResposta = it }, label = { Text("Sua Resposta") }, modifier = Modifier.fillMaxWidth()) } },
            confirmButton = { Button(onClick = { if (textoResposta.isNotBlank()) { db.collection("suporte").document(mensagemParaResponder!!.id).update(mapOf("resposta" to textoResposta, "dataResposta" to Date())); mensagemParaResponder = null; textoResposta = "" } }) { Text("Enviar Resposta") } },
            dismissButton = { TextButton(onClick = { mensagemParaResponder = null }) { Text("Cancelar") } }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Painel Administrativo") },
                navigationIcon = {
                    IconButton(onClick = onVoltar) { Icon(Icons.Default.ArrowBack, "Voltar") }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            // Barra de Abas
            ScrollableTabRow(selectedTabIndex = aba, edgePadding = 0.dp) {
                Tab(selected = aba == 0, onClick = { aba = 0 }, text = { Text("Online") })
                Tab(selected = aba == 1, onClick = { aba = 1 }, text = { Text("Histórico") })
                Tab(selected = aba == 2, onClick = { aba = 2 }, text = { Text("Suporte") })
                Tab(selected = aba == 3, onClick = { aba = 3 }, text = { Text("Banir") })
                Tab(selected = aba == 4, onClick = { aba = 4 }, text = { Text("Ferramentas") }) // Nova Aba
            }

            if (carregando) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    when (aba) {
                        0 -> { // ONLINE
                            item { Text("Usuários ativos no último minuto: ${usuariosOnline.size}", fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp)) }
                            items(usuariosOnline) { user -> ItemUsuario(user, true) }
                        }
                        1 -> { // HISTÓRICO
                            item { Text("Total de usuários registrados: ${listaUsuarios.size}", fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp)) }
                            items(listaUsuarios) { user -> ItemUsuario(user, false) }
                        }
                        2 -> { // SUPORTE
                            if (listaSuporte.isEmpty()) item { Text("Nenhuma mensagem de suporte.") }
                            items(listaSuporte) { msg ->
                                Card(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { mensagemParaResponder = msg },
                                    colors = CardDefaults.cardColors(containerColor = if (msg.resposta.isEmpty()) Color(0xFF01645C) else Color(0xFF313131))
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
                        3 -> { // BANIR
                            item {
                                Column {
                                    Text("Banimento de Usuários", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                    Spacer(modifier = Modifier.height(16.dp))
                                    OutlinedTextField(value = novoBanimento, onValueChange = { novoBanimento = it }, label = { Text("Nome do usuário para banir") }, modifier = Modifier.fillMaxWidth())
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Button(onClick = { if (novoBanimento.isNotBlank()) { db.collection("usuarios_banidos").document(novoBanimento.lowercase()).set(hashMapOf("data" to Date())); novoBanimento = "" } }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) { Text("BANIR USUÁRIO") }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("Nota: Banir impede o login futuro deste nome. O usuário não será notificado imediatamente, mas não conseguirá entrar novamente.", fontSize = 12.sp, color = Color.Gray)
                                }
                            }
                        }
                        4 -> { // FERRAMENTAS (NOVO)
                            item {
                                Column {
                                    Text("Ferramentas de Banco de Dados", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                    Spacer(modifier = Modifier.height(16.dp))

                                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF333333))) {
                                        Column(modifier = Modifier.padding(16.dp)) {
                                            Text("Importação de Produtos (CSV)", fontWeight = FontWeight.Bold, color = Color.White)
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text("Lê o arquivo 'res/raw/produtos.csv' e adiciona os itens ao Firestore na coleção 'produtos_base'. Use isso para popular o banco inicial.", fontSize = 12.sp, color = Color.LightGray)
                                            Spacer(modifier = Modifier.height(12.dp))
                                            Button(
                                                onClick = {
                                                    // Chama a função utilitária de importação
                                                    com.example.compare.utils.importarProdutosDoCsv(context)
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800)),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text("Iniciar Importação CSV")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ItemUsuario(user: Usuario, isOnline: Boolean) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF252525))) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(if (isOnline) Color(0xFF01D33D) else Color.Gray))
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(user.nome, fontWeight = FontWeight.Bold, color = Color.White)
                Text(if (isOnline) "Online agora" else "Último: ${formatarData(user.ultimoAcesso)}", fontSize = 10.sp, color = Color.LightGray)
            }
        }
    }
}