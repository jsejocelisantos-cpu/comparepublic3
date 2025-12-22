package com.example.compare.screens

import android.graphics.Bitmap
import android.view.ViewGroup
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.graphicsLayer // <--- CORREÇÃO 4
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.compare.R
import com.example.compare.model.DadosMercado
import com.example.compare.model.ProdutoPreco
import com.example.compare.utils.bitmapParaString // <--- CORREÇÃO 2 (Nome correto)
import com.example.compare.utils.dadosBrasil
import com.example.compare.utils.stringParaBitmap
import com.example.compare.utils.temOfensa
import com.google.firebase.firestore.FirebaseFirestore
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import java.util.Date
import java.util.concurrent.Executor

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

    // --- BUSCA AUTOMÁTICA DE PRODUTOS ---
    LaunchedEffect(codigoBarras) {
        if (codigoBarras.length >= 8 && nomeProduto.isEmpty() && !buscandoProduto) {
            buscandoProduto = true
            db.collection("produtos_base").document(codigoBarras).get()
                .addOnSuccessListener { doc ->
                    if (doc.exists()) {
                        nomeProduto = doc.getString("nomeProduto") ?: ""
                        Toast.makeText(context, "Produto encontrado no catálogo!", Toast.LENGTH_SHORT).show()
                        buscandoProduto = false
                    } else {
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

    LaunchedEffect(cidadeSelecionada) {
        if (cidadeSelecionada.isNotEmpty()) {
            db.collection("mercados").whereEqualTo("cidade", cidadeSelecionada).get()
                .addOnSuccessListener { res -> listaMercados = res.documents.mapNotNull { it.getString("nome") } }
        }
    }

    LaunchedEffect(mercado) {
        sugestoesMercado = if (mercado.isBlank()) emptyList() else listaMercados.filter { it.contains(mercado, ignoreCase = true) }
        expandirMercado = sugestoesMercado.isNotEmpty()
    }

    if (mostrarCamera) {
        // CORREÇÃO 1: Função CameraPreview agora existe (definida abaixo)
        CameraPreview(
            onFotoTirada = { bitmap ->
                fotoBase64 = bitmapParaString(bitmap) // CORREÇÃO 2: Nome correto da função
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

                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = codigoBarras,
                            onValueChange = { if (it.length <= 13 && it.all { c -> c.isDigit() }) codigoBarras = it },
                            label = { Text("Código de Barras") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            trailingIcon = {
                                IconButton(onClick = {
                                    scanner.startScan()
                                        .addOnSuccessListener { barcode -> barcode.rawValue?.let { codigoBarras = it } }
                                }) {
                                    Image(painter = painterResource(id = R.drawable.scancode), contentDescription = "Scan", modifier = Modifier.size(30.dp))
                                }
                            }
                        )
                    }
                    if (buscandoProduto) Text("Buscando...", fontSize = 12.sp, color = Color.Blue)
                    Spacer(modifier = Modifier.height(8.dp))
                }

                item {
                    OutlinedTextField(value = nomeProduto, onValueChange = { nomeProduto = it }, label = { Text("Nome do Produto") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next))
                    Spacer(modifier = Modifier.height(8.dp))
                }

                item {
                    OutlinedTextField(value = valor, onValueChange = { newText -> if (newText.count { it == '.' } <= 1 && newText.all { it.isDigit() || it == '.' }) valor = newText }, label = { Text("Preço (R$)") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next))
                    Spacer(modifier = Modifier.height(8.dp))
                }

                item {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(value = mercado, onValueChange = { mercado = it }, label = { Text("Nome do Mercado") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }))
                        DropdownMenu(expanded = expandirMercado, onDismissRequest = { expandirMercado = false }, properties = androidx.compose.ui.window.PopupProperties(focusable = false)) {
                            sugestoesMercado.forEach { m -> DropdownMenuItem(text = { Text(m) }, onClick = { mercado = m; expandirMercado = false }) }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                item {
                    Text("Localização", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                    LazyRow(modifier = Modifier.padding(vertical = 8.dp)) {
                        items(dadosBrasil.keys.sorted()) { uf -> FilterChip(selected = estadoSelecionado == uf, onClick = { estadoSelecionado = uf; cidadeSelecionada = ""; buscaCidade = "" }, label = { Text(uf) }, modifier = Modifier.padding(end = 4.dp)) }
                    }
                    if (estadoSelecionado.isNotEmpty()) {
                        Box {
                            OutlinedTextField(value = if(expandirCidades) buscaCidade else cidadeSelecionada, onValueChange = { buscaCidade = it; expandirCidades = true }, label = { Text("Cidade") }, modifier = Modifier.fillMaxWidth(), trailingIcon = { IconButton(onClick = { expandirCidades = !expandirCidades }) { Icon(Icons.Default.ArrowBack, null, modifier = Modifier.rotate(270f)) } })
                            DropdownMenu(expanded = expandirCidades, onDismissRequest = { expandirCidades = false }, modifier = Modifier.heightIn(max = 200.dp)) {
                                val filtradas = dadosBrasil[estadoSelecionado]?.filter { it.contains(buscaCidade, true) } ?: emptyList()
                                filtradas.forEach { cid -> DropdownMenuItem(text = { Text(cid) }, onClick = { cidadeSelecionada = cid; expandirCidades = false; buscaCidade = "" }) }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(onClick = { mostrarCamera = true }) { Icon(Icons.Default.CameraAlt, null); Spacer(Modifier.width(8.dp)); Text(if (fotoBase64.isEmpty()) "Foto" else "Trocar") }
                        if (fotoBase64.isNotEmpty()) { Spacer(Modifier.width(10.dp)); val bitmap = stringParaBitmap(fotoBase64); if (bitmap != null) Image(bitmap = bitmap.asImageBitmap(), contentDescription = null, modifier = Modifier.size(50.dp), contentScale = ContentScale.Crop) }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = comentario, onValueChange = { comentario = it }, label = { Text("Obs (opcional)") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(16.dp))
                }

                item {
                    Button(
                        onClick = {
                            if (nomeProduto.isBlank() || valor.isBlank() || mercado.isBlank() || cidadeSelecionada.isBlank()) {
                                Toast.makeText(context, "Preencha tudo!", Toast.LENGTH_SHORT).show()
                            } else if (temOfensa(nomeProduto) || temOfensa(comentario) || temOfensa(mercado)) {
                                Toast.makeText(context, "Conteúdo impróprio.", Toast.LENGTH_SHORT).show()
                            } else {
                                salvando = true
                                val novaOferta = ProdutoPreco(
                                    id = produtoPreenchido?.id ?: "",
                                    usuarioId = usuarioNome,
                                    nomeProduto = nomeProduto,
                                    nomePesquisa = nomeProduto.lowercase(),
                                    mercado = mercado,
                                    valor = valor.toDoubleOrNull() ?: 0.0,
                                    data = Date(),
                                    codigoBarras = codigoBarras,
                                    fotoBase64 = fotoBase64,
                                    comentario = comentario,
                                    estado = estadoSelecionado,
                                    cidade = cidadeSelecionada
                                )

                                val docRef = if (produtoPreenchido == null) db.collection("ofertas").document() else db.collection("ofertas").document(novaOferta.id)
                                val ofertaFinal = if (produtoPreenchido == null) novaOferta.copy(id = docRef.id) else novaOferta

                                docRef.set(ofertaFinal).addOnSuccessListener {
                                    // CORREÇÃO 3: Agora DadosMercado aceita a cidade (se você atualizou o Modelos.kt)
                                    db.collection("mercados").document(mercado).set(DadosMercado(mercado, mercado, "", "", "", cidadeSelecionada))
                                    Toast.makeText(context, "Salvo!", Toast.LENGTH_SHORT).show()
                                    onSalvar()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        enabled = !salvando
                    ) { if (salvando) CircularProgressIndicator(color = Color.White) else Text("SALVAR") }
                }
            }
        }
    }
}

// --- IMPLEMENTAÇÃO BÁSICA DA CÂMERA (CORREÇÃO 1) ---
@Composable
fun CameraPreview(onFotoTirada: (Bitmap) -> Unit, onFechar: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val imageCapture = remember { ImageCapture.Builder().build() }
    val executor = remember { ContextCompat.getMainExecutor(context) }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val preview = Preview.Builder().build()
                val selector = CameraSelector.DEFAULT_BACK_CAMERA
                preview.setSurfaceProvider(previewView.surfaceProvider)
                try {
                    val cameraProvider = cameraProviderFuture.get()
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview, imageCapture)
                } catch (e: Exception) { }
                previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        // Botão Fechar
        IconButton(onClick = onFechar, modifier = Modifier.align(Alignment.TopStart).padding(16.dp)) {
            Icon(Icons.Default.ArrowBack, "Voltar", tint = Color.White)
        }

        // Botão Capturar
        Button(
            onClick = {
                imageCapture.takePicture(executor, object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: androidx.camera.core.ImageProxy) {
                        val bitmap = image.toBitmap()
                        // Redimensiona para economizar memória e o Firestore aceitar
                        val scaled = android.graphics.Bitmap.createScaledBitmap(bitmap, 600, (600.0 * bitmap.height / bitmap.width).toInt(), true)
                        // Corrige rotação se necessário (aqui simplificado, pois toBitmap geralmente resolve no CameraX novo)
                        val matrix = android.graphics.Matrix()
                        matrix.postRotate(image.imageInfo.rotationDegrees.toFloat())
                        val rotated = android.graphics.Bitmap.createBitmap(scaled, 0, 0, scaled.width, scaled.height, matrix, true)

                        onFotoTirada(rotated)
                        image.close()
                    }
                    override fun onError(exception: ImageCaptureException) {
                        Toast.makeText(context, "Erro ao capturar", Toast.LENGTH_SHORT).show()
                    }
                })
            },
            modifier = Modifier.align(Alignment.BottomCenter).padding(32.dp).size(80.dp),
            shape = androidx.compose.foundation.shape.CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = Color.White)
        ) {
            Icon(Icons.Default.CameraAlt, null, tint = Color.Black)
        }
    }
}

fun Modifier.rotate(degrees: Float) = this.then(Modifier.graphicsLayer(rotationZ = degrees))