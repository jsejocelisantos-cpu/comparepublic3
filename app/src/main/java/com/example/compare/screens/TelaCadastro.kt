package com.example.compare.screens

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.compare.R
import com.example.compare.model.DadosMercado
import com.example.compare.model.ProdutoPreco
import com.example.compare.utils.comprimirImagem
import com.example.compare.utils.dadosBrasil
import com.example.compare.utils.stringParaBitmap
import com.example.compare.utils.temOfensa
import com.google.firebase.firestore.FirebaseFirestore
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelaCadastro(
    usuarioNome: String,
    produtoPreenchido: ProdutoPreco?,
    estadoPre: String,
    cidadePre: String,
    onVoltar: () -> Unit,
    onSalvar: () -> Unit
) {
    val db = FirebaseFirestore.getInstance()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val scanner = remember { GmsBarcodeScanning.getClient(context) }

    // Campos do formulário
    var codigoBarras by remember { mutableStateOf(produtoPreenchido?.codigoBarras ?: "") }
    var nomeProduto by remember { mutableStateOf(produtoPreenchido?.nomeProduto ?: "") }
    var valor by remember { mutableStateOf(if (produtoPreenchido != null) produtoPreenchido.valor.toString() else "") }
    var mercado by remember { mutableStateOf(produtoPreenchido?.mercado ?: "") }
    var comentario by remember { mutableStateOf(produtoPreenchido?.comentario ?: "") }

    // Foto
    var fotoBase64 by remember { mutableStateOf(produtoPreenchido?.fotoBase64 ?: "") }
    var mostrarCamera by remember { mutableStateOf(false) }

    // Localização
    var estadoSelecionado by remember { mutableStateOf(if (produtoPreenchido?.estado?.isNotEmpty() == true) produtoPreenchido.estado else estadoPre) }
    var cidadeSelecionada by remember { mutableStateOf(if (produtoPreenchido?.cidade?.isNotEmpty() == true) produtoPreenchido.cidade else cidadePre) }
    var buscaCidade by remember { mutableStateOf("") }
    var expandirCidades by remember { mutableStateOf(false) }

    // Mercados
    var listaMercados by remember { mutableStateOf(listOf<String>()) }
    var sugestoesMercado by remember { mutableStateOf(listOf<String>()) }
    var expandirMercado by remember { mutableStateOf(false) }

    // Controle de Loading e Feedback
    var salvando by remember { mutableStateOf(false) }
    var buscandoProduto by remember { mutableStateOf(false) }

    // --- BUSCA AUTOMÁTICA DE PRODUTOS (CATÁLOGO + OFERTAS) ---
    LaunchedEffect(codigoBarras) {
        if (codigoBarras.length >= 8 && nomeProduto.isEmpty() && !buscandoProduto) {
            buscandoProduto = true

            // 1º Tenta buscar na coleção 'produtos_base' (Sua Importação CSV)
            db.collection("produtos_base").document(codigoBarras).get()
                .addOnSuccessListener { doc ->
                    if (doc.exists()) {
                        nomeProduto = doc.getString("nomeProduto") ?: ""
                        Toast.makeText(context, "Produto encontrado no catálogo!", Toast.LENGTH_SHORT).show()
                        buscandoProduto = false
                    } else {
                        // 2º Se não achar no catálogo, tenta nas ofertas antigas (Histórico de usuários)
                        db.collection("ofertas")
                            .whereEqualTo("codigoBarras", codigoBarras)
                            .limit(1)
                            .get()
                            .addOnSuccessListener { result ->
                                if (!result.isEmpty) {
                                    val p = result.documents[0].toObject(ProdutoPreco::class.java)
                                    if (p != null) {
                                        nomeProduto = p.nomeProduto
                                        if (fotoBase64.isEmpty()) fotoBase64 = p.fotoBase64
                                        Toast.makeText(context, "Produto já cadastrado antes!", Toast.LENGTH_SHORT).show()
                                    }
                                }
                                buscandoProduto = false
                            }
                            .addOnFailureListener { buscandoProduto = false }
                    }
                }
                .addOnFailureListener { buscandoProduto = false }
        }
    }

    // Carregar sugestões de mercados na cidade
    LaunchedEffect(cidadeSelecionada) {
        if (cidadeSelecionada.isNotEmpty()) {
            db.collection("mercados").whereEqualTo("cidade", cidadeSelecionada).get()
                .addOnSuccessListener { res -> listaMercados = res.documents.mapNotNull { it.getString("nome") } }
        }
    }

    // Filtro de Mercados
    LaunchedEffect(mercado) {
        sugestoesMercado = if (mercado.isBlank()) emptyList() else listaMercados.filter { it.contains(mercado, ignoreCase = true) }
        expandirMercado = sugestoesMercado.isNotEmpty()
    }

    if (mostrarCamera) {
        CameraPreview(
            onFotoTirada = { bitmap ->
                fotoBase64 = comprimirImagem(bitmap)
                mostrarCamera = false
            },
            onFechar = { mostrarCamera = false }
        )
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(if (produtoPreenchido == null) "Nova Oferta" else "Editar Oferta") },
                    navigationIcon = { IconButton(onClick = onVoltar) { Icon(Icons.Default.ArrowBack, "Voltar") } }
                )
            }
        ) { padding ->
            LazyColumn(modifier = Modifier.padding(padding).padding(16.dp).fillMaxSize()) {

                // --- SEÇÃO CÓDIGO DE BARRAS ---
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = codigoBarras,
                            onValueChange = { if (it.length <= 13 && it.all { c -> c.isDigit() }) codigoBarras = it },
                            label = { Text("Código de Barras (Opcional)") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            trailingIcon = {
                                IconButton(onClick = {
                                    scanner.startScan()
                                        .addOnSuccessListener { barcode -> barcode.rawValue?.let { codigoBarras = it } }
                                        .addOnFailureListener { Toast.makeText(context, "Erro Câmera", Toast.LENGTH_SHORT).show() }
                                }) {
                                    Image(painter = painterResource(id = R.drawable.scancode), contentDescription = "Scan", modifier = Modifier.size(30.dp))
                                }
                            }
                        )
                    }
                    if (buscandoProduto) Text("Buscando produto...", fontSize = 12.sp, color = Color.Blue)
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // --- SEÇÃO NOME DO PRODUTO ---
                item {
                    OutlinedTextField(
                        value = nomeProduto,
                        onValueChange = { nomeProduto = it },
                        label = { Text("Nome do Produto") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // --- SEÇÃO PREÇO ---
                item {
                    OutlinedTextField(
                        value = valor,
                        onValueChange = { newText ->
                            if (newText.count { it == '.' } <= 1 && newText.all { it.isDigit() || it == '.' }) valor = newText
                        },
                        label = { Text("Preço (R$)") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // --- SEÇÃO MERCADO ---
                item {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = mercado,
                            onValueChange = { mercado = it },
                            label = { Text("Nome do Mercado") },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
                        )
                        DropdownMenu(
                            expanded = expandirMercado,
                            onDismissRequest = { expandirMercado = false },
                            properties = androidx.compose.ui.window.PopupProperties(focusable = false)
                        ) {
                            sugestoesMercado.forEach { m ->
                                DropdownMenuItem(text = { Text(m) }, onClick = { mercado = m; expandirMercado = false })
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // --- SEÇÃO LOCALIZAÇÃO ---
                item {
                    Text("Localização da Oferta", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                    LazyRow(modifier = Modifier.padding(vertical = 8.dp)) {
                        items(dadosBrasil.keys.sorted()) { uf ->
                            FilterChip(selected = estadoSelecionado == uf, onClick = { estadoSelecionado = uf; cidadeSelecionada = ""; buscaCidade = "" }, label = { Text(uf) }, modifier = Modifier.padding(end = 4.dp))
                        }
                    }
                    if (estadoSelecionado.isNotEmpty()) {
                        Box {
                            OutlinedTextField(
                                value = if(expandirCidades) buscaCidade else cidadeSelecionada,
                                onValueChange = { buscaCidade = it; expandirCidades = true },
                                label = { Text("Cidade") },
                                modifier = Modifier.fillMaxWidth(),
                                trailingIcon = { IconButton(onClick = { expandirCidades = !expandirCidades }) { Icon(Icons.Default.ArrowBack, null, modifier = Modifier.rotate(270f)) } } // Seta pra baixo improvisada
                            )
                            DropdownMenu(
                                expanded = expandirCidades,
                                onDismissRequest = { expandirCidades = false },
                                modifier = Modifier.heightIn(max = 200.dp)
                            ) {
                                val filtradas = dadosBrasil[estadoSelecionado]?.filter { it.contains(buscaCidade, true) } ?: emptyList()
                                filtradas.forEach { cid ->
                                    DropdownMenuItem(text = { Text(cid) }, onClick = { cidadeSelecionada = cid; expandirCidades = false; buscaCidade = "" })
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // --- SEÇÃO FOTO E COMENTÁRIO ---
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(onClick = { mostrarCamera = true }) {
                            Icon(Icons.Default.CameraAlt, null); Spacer(Modifier.width(8.dp)); Text(if (fotoBase64.isEmpty()) "Adicionar Foto" else "Trocar Foto")
                        }
                        if (fotoBase64.isNotEmpty()) {
                            Spacer(Modifier.width(10.dp))
                            val bitmap = stringParaBitmap(fotoBase64)
                            if (bitmap != null) {
                                Image(bitmap = bitmap.asImageBitmap(), contentDescription = null, modifier = Modifier.size(50.dp), contentScale = ContentScale.Crop)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = comentario, onValueChange = { comentario = it }, label = { Text("Observação (opcional)") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // --- BOTÃO SALVAR ---
                item {
                    Button(
                        onClick = {
                            if (nomeProduto.isBlank() || valor.isBlank() || mercado.isBlank() || cidadeSelecionada.isBlank()) {
                                Toast.makeText(context, "Preencha nome, preço, mercado e cidade!", Toast.LENGTH_SHORT).show()
                            } else if (temOfensa(nomeProduto) || temOfensa(comentario) || temOfensa(mercado)) {
                                Toast.makeText(context, "Conteúdo impróprio detectado.", Toast.LENGTH_SHORT).show()
                            } else {
                                salvando = true
                                val precoDouble = valor.toDoubleOrNull() ?: 0.0
                                val novaOferta = ProdutoPreco(
                                    id = produtoPreenchido?.id ?: "", // Mantém ID se editando
                                    usuarioId = usuarioNome,
                                    nomeProduto = nomeProduto,
                                    nomePesquisa = nomeProduto.lowercase(),
                                    mercado = mercado,
                                    valor = precoDouble,
                                    data = Date(),
                                    codigoBarras = codigoBarras,
                                    fotoBase64 = fotoBase64,
                                    comentario = comentario,
                                    estado = estadoSelecionado,
                                    cidade = cidadeSelecionada
                                )

                                if (produtoPreenchido == null) {
                                    db.collection("ofertas").add(novaOferta)
                                        .addOnSuccessListener {
                                            // Salva mercado para autocomplete futuro
                                            db.collection("mercados").document(mercado).set(DadosMercado(mercado, mercado, "", "", "", cidadeSelecionada))
                                            Toast.makeText(context, "Oferta publicada!", Toast.LENGTH_SHORT).show()
                                            onSalvar()
                                        }
                                } else {
                                    db.collection("ofertas").document(novaOferta.id).set(novaOferta)
                                        .addOnSuccessListener {
                                            Toast.makeText(context, "Oferta atualizada!", Toast.LENGTH_SHORT).show()
                                            onSalvar()
                                        }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        enabled = !salvando
                    ) {
                        if (salvando) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp)) else Text("SALVAR OFERTA")
                    }
                }
            }
        }
    }
}
// Extensãozinha auxiliar para rotacionar icone
fun Modifier.rotate(degrees: Float) = this.then(Modifier.graphicsLayer(rotationZ = degrees))