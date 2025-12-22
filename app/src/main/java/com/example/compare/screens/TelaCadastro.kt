package com.example.compare.screens

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
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
import com.example.compare.utils.bitmapParaString
import com.example.compare.utils.stringParaBitmap
import com.example.compare.utils.temOfensa
import com.google.firebase.firestore.FirebaseFirestore
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import java.text.NumberFormat
import java.util.Date
import java.util.Locale

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

    // --- ESTADOS DOS CAMPOS ---
    var codigoBarras by remember { mutableStateOf(produtoPreenchido?.codigoBarras ?: "") }
    var nomeProduto by remember { mutableStateOf(produtoPreenchido?.nomeProduto ?: "") }

    // Lógica de Preço (Money Mask)
    var valorRaw by remember { mutableStateOf(if (produtoPreenchido != null) (produtoPreenchido.valor * 100).toLong().toString() else "") }
    val valorFormatado = remember(valorRaw) {
        try {
            val v = valorRaw.toLongOrNull() ?: 0L
            val doubleVal = v / 100.0
            NumberFormat.getCurrencyInstance(Locale("pt", "BR")).format(doubleVal).replace("R$", "").trim()
        } catch (e: Exception) { "" }
    }

    var mercado by remember { mutableStateOf(produtoPreenchido?.mercado ?: "") }
    var comentario by remember { mutableStateOf(produtoPreenchido?.comentario ?: "") }

    // --- FOTO (CÂMERA E GALERIA) ---
    var fotoBase64 by remember { mutableStateOf(produtoPreenchido?.fotoBase64 ?: "") }
    var mostrarCamera by remember { mutableStateOf(false) }
    var mostrarOpcoesFoto by remember { mutableStateOf(false) } // Dialog para escolher

    // Launcher da Galeria
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val bitmap = if (Build.VERSION.SDK_INT < 28) {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                } else {
                    val source = ImageDecoder.createSource(context.contentResolver, uri)
                    ImageDecoder.decodeBitmap(source)
                }
                // Redimensiona para evitar travar o Firebase (Max 1MB recomendado)
                val scaled = Bitmap.createScaledBitmap(bitmap, 600, (600.0 * bitmap.height / bitmap.width).toInt(), true)
                fotoBase64 = bitmapParaString(scaled)
            } catch (e: Exception) {
                Toast.makeText(context, "Erro ao carregar imagem", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // --- LOCALIZAÇÃO E MERCADOS ---
    val estadoSelecionado = if (produtoPreenchido?.estado?.isNotEmpty() == true) produtoPreenchido.estado else estadoPre
    val cidadeSelecionada = if (produtoPreenchido?.cidade?.isNotEmpty() == true) produtoPreenchido.cidade else cidadePre

    var listaMercados by remember { mutableStateOf(listOf<String>()) }
    var expandirMercado by remember { mutableStateOf(false) }

    // Filtro inteligente: Se digitar, filtra. Se vazio, mostra todos (ao clicar na seta).
    val sugestoesMercado = remember(mercado, listaMercados) {
        if (mercado.isBlank()) listaMercados else listaMercados.filter { it.contains(mercado, ignoreCase = true) }
    }

    // Controle de UI
    var salvando by remember { mutableStateOf(false) }
    var buscandoProduto by remember { mutableStateOf(false) }

    // --- BUSCA AUTOMÁTICA DE PRODUTO ---
    LaunchedEffect(codigoBarras) {
        if (codigoBarras.length >= 8 && nomeProduto.isEmpty() && !buscandoProduto) {
            buscandoProduto = true
            // 1. Busca no Catálogo Base (CSV Importado)
            db.collection("produtos_base").document(codigoBarras).get()
                .addOnSuccessListener { doc ->
                    if (doc.exists()) {
                        nomeProduto = doc.getString("nomeProduto") ?: ""
                        Toast.makeText(context, "Produto encontrado!", Toast.LENGTH_SHORT).show()
                        buscandoProduto = false
                    } else {
                        // 2. Busca no Histórico de Ofertas
                        db.collection("ofertas").whereEqualTo("codigoBarras", codigoBarras).limit(1).get()
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

    // Carregar lista de mercados da cidade
    LaunchedEffect(cidadeSelecionada) {
        if (cidadeSelecionada.isNotEmpty()) {
            db.collection("mercados").whereEqualTo("cidade", cidadeSelecionada).get()
                .addOnSuccessListener { res -> listaMercados = res.documents.mapNotNull { it.getString("nome") } }
        }
    }

    // Renderização
    if (mostrarCamera) {
        CameraPreview(
            onFotoTirada = { bitmap ->
                fotoBase64 = bitmapParaString(bitmap)
                mostrarCamera = false
            },
            onFechar = { mostrarCamera = false }
        )
    } else {
        // Dialog para escolher Foto ou Galeria
        if (mostrarOpcoesFoto) {
            AlertDialog(
                onDismissRequest = { mostrarOpcoesFoto = false },
                title = { Text("Adicionar Foto") },
                text = {
                    Column {
                        ListItem(
                            headlineContent = { Text("Tirar Foto") },
                            leadingContent = { Icon(Icons.Default.CameraAlt, null) },
                            modifier = Modifier.clickable { mostrarOpcoesFoto = false; mostrarCamera = true }
                        )
                        ListItem(
                            headlineContent = { Text("Escolher da Galeria") },
                            leadingContent = { Icon(Icons.Default.PhotoLibrary, null) },
                            modifier = Modifier.clickable { mostrarOpcoesFoto = false; galleryLauncher.launch("image/*") }
                        )
                    }
                },
                confirmButton = { TextButton(onClick = { mostrarOpcoesFoto = false }) { Text("Cancelar") } }
            )
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(if (produtoPreenchido == null) "Nova Oferta" else "Editar Oferta")
                            Text(cidadeSelecionada, fontSize = 12.sp, fontWeight = FontWeight.Normal)
                        }
                    },
                    navigationIcon = { IconButton(onClick = onVoltar) { Icon(Icons.Default.ArrowBack, "Voltar") } }
                )
            }
        ) { padding ->
            LazyColumn(modifier = Modifier.padding(padding).padding(16.dp).fillMaxSize()) {

                // --- LINHA 1: SCANNER + FOTO ---
                item {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = codigoBarras,
                            onValueChange = { if (it.length <= 13 && it.all { c -> c.isDigit() }) codigoBarras = it },
                            label = { Text("Código Barras") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            trailingIcon = {
                                IconButton(onClick = {
                                    scanner.startScan().addOnSuccessListener { barcode -> barcode.rawValue?.let { codigoBarras = it } }
                                }) {
                                    Image(painter = painterResource(id = R.drawable.scancode), contentDescription = "Scan", modifier = Modifier.size(30.dp))
                                }
                            }
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        // Box da Foto (Abre Dialog Opções)
                        Box(
                            modifier = Modifier
                                .size(60.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.LightGray)
                                .border(1.dp, Color.Gray, RoundedCornerShape(8.dp))
                                .clickable { mostrarOpcoesFoto = true },
                            contentAlignment = Alignment.Center
                        ) {
                            if (fotoBase64.isNotEmpty()) {
                                val bitmap = stringParaBitmap(fotoBase64)
                                if (bitmap != null) {
                                    Image(bitmap = bitmap.asImageBitmap(), contentDescription = "Foto", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                }
                            } else {
                                Icon(Icons.Default.CameraAlt, contentDescription = "Foto", tint = Color.DarkGray)
                            }
                        }
                    }
                    if (buscandoProduto) Text("Buscando no catálogo...", fontSize = 12.sp, color = Color.Blue)
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // --- LINHA 2: NOME ---
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

                // --- LINHA 3: PREÇO (COM MÁSCARA) ---
                item {
                    OutlinedTextField(
                        value = valorFormatado,
                        onValueChange = { newValue ->
                            val digits = newValue.filter { it.isDigit() }
                            if (digits.length <= 10) valorRaw = digits
                        },
                        label = { Text("Preço (R$)") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Next),
                        prefix = { Text("R$ ") }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // --- LINHA 4: MERCADO (AUTOCOMPLETE COM SETA) ---
                item {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = mercado,
                            onValueChange = {
                                mercado = it
                                expandirMercado = true // Abre ao digitar
                            },
                            label = { Text("Nome do Mercado") },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                            trailingIcon = {
                                // Seta para abrir a lista completa
                                IconButton(onClick = { expandirMercado = !expandirMercado }) {
                                    Icon(Icons.Default.ArrowDropDown, "Lista de Mercados")
                                }
                            }
                        )

                        DropdownMenu(
                            expanded = expandirMercado,
                            onDismissRequest = { expandirMercado = false },
                            properties = androidx.compose.ui.window.PopupProperties(focusable = false),
                            modifier = Modifier.fillMaxWidth(0.9f).heightIn(max = 200.dp)
                        ) {
                            if (sugestoesMercado.isEmpty()) {
                                DropdownMenuItem(text = { Text("Nenhum mercado encontrado", color = Color.Gray) }, onClick = { })
                            } else {
                                sugestoesMercado.forEach { m ->
                                    DropdownMenuItem(text = { Text(m) }, onClick = { mercado = m; expandirMercado = false })
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // --- LINHA 5: OBSERVAÇÃO ---
                item {
                    OutlinedTextField(value = comentario, onValueChange = { comentario = it }, label = { Text("Observação (opcional)") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(24.dp))
                }

                // --- BOTÃO SALVAR ---
                item {
                    Button(
                        onClick = {
                            if (nomeProduto.isBlank() || valorRaw.isBlank() || mercado.isBlank()) {
                                Toast.makeText(context, "Preencha nome, preço e mercado!", Toast.LENGTH_SHORT).show()
                            } else if (temOfensa(nomeProduto) || temOfensa(comentario) || temOfensa(mercado)) {
                                Toast.makeText(context, "Conteúdo impróprio.", Toast.LENGTH_SHORT).show()
                            } else {
                                salvando = true
                                val valorFinal = (valorRaw.toLongOrNull() ?: 0L) / 100.0

                                val novaOferta = ProdutoPreco(
                                    id = produtoPreenchido?.id ?: "",
                                    usuarioId = usuarioNome,
                                    nomeProduto = nomeProduto,
                                    nomePesquisa = nomeProduto.lowercase(),
                                    mercado = mercado,
                                    valor = valorFinal,
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

// Implementação da Câmera (Igual ao anterior)
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
        IconButton(onClick = onFechar, modifier = Modifier.align(Alignment.TopStart).padding(16.dp)) { Icon(Icons.Default.ArrowBack, "Voltar", tint = Color.White) }
        Button(
            onClick = {
                imageCapture.takePicture(executor, object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: androidx.camera.core.ImageProxy) {
                        val bitmap = image.toBitmap()
                        val scaled = android.graphics.Bitmap.createScaledBitmap(bitmap, 600, (600.0 * bitmap.height / bitmap.width).toInt(), true)
                        val matrix = android.graphics.Matrix()
                        matrix.postRotate(image.imageInfo.rotationDegrees.toFloat())
                        val rotated = android.graphics.Bitmap.createBitmap(scaled, 0, 0, scaled.width, scaled.height, matrix, true)
                        onFotoTirada(rotated)
                        image.close()
                    }
                    override fun onError(exception: ImageCaptureException) { Toast.makeText(context, "Erro ao capturar", Toast.LENGTH_SHORT).show() }
                })
            },
            modifier = Modifier.align(Alignment.BottomCenter).padding(32.dp).size(80.dp),
            shape = androidx.compose.foundation.shape.CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = Color.White)
        ) { Icon(Icons.Default.CameraAlt, null, tint = Color.Black) }
    }
}

fun Modifier.rotate(degrees: Float) = this.then(Modifier.graphicsLayer(rotationZ = degrees))