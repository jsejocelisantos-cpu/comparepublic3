package com.example.compare.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import androidx.core.content.ContextCompat
import com.example.compare.R
import com.example.compare.model.ProdutoPreco
import com.example.compare.utils.bitmapParaString
import com.example.compare.utils.stringParaBitmap
import com.example.compare.utils.temOfensa
import com.google.firebase.firestore.FirebaseFirestore
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import java.util.Date
import java.util.Locale

@Composable
fun TelaEdicaoInterna(oferta: ProdutoPreco, onCancel: () -> Unit, onSave: (ProdutoPreco) -> Unit) {
    var nomeEd by remember { mutableStateOf(oferta.nomeProduto) }
    var precoEd by remember { mutableStateOf(oferta.valor.toString()) }
    var codEd by remember { mutableStateOf(oferta.codigoBarras) }
    var fotoBitmap by remember { mutableStateOf<Bitmap?>(stringParaBitmap(oferta.fotoBase64)) }

    val context = LocalContext.current
    val launcherGaleria = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val stream = context.contentResolver.openInputStream(uri)
            fotoBitmap = BitmapFactory.decodeStream(stream)
        }
    }

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Editar Oferta") },
        text = {
            Column {
                Box(
                    modifier = Modifier.fillMaxWidth().height(100.dp).background(Color.LightGray).clickable { launcherGaleria.launch("image/*") },
                    contentAlignment = Alignment.Center
                ) {
                    if (fotoBitmap != null) {
                        Image(bitmap = fotoBitmap!!.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    } else {
                        Text("Toque p/ Foto", fontSize = 12.sp)
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(value = nomeEd, onValueChange = { nomeEd = it }, label = { Text("Nome") })
                OutlinedTextField(value = precoEd, onValueChange = { precoEd = it }, label = { Text("Preço") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                OutlinedTextField(value = codEd, onValueChange = { codEd = it }, label = { Text("Código Barras") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            }
        },
        confirmButton = {
            Button(onClick = {
                val novoPreco = precoEd.replace(",", ".").toDoubleOrNull() ?: 0.0
                val novaFoto = if (fotoBitmap != null) bitmapParaString(fotoBitmap!!) else ""

                if (nomeEd.isNotEmpty() && !temOfensa(nomeEd)) {
                    // ATENÇÃO: Salvando nomePesquisa em minúsculo na edição também
                    val nova = oferta.copy(
                        nomeProduto = nomeEd,
                        nomePesquisa = nomeEd.lowercase(),
                        valor = novoPreco,
                        codigoBarras = codEd,
                        fotoBase64 = novaFoto
                    )
                    onSave(nova)
                }
            }) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancelar") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelaCadastro(
    usuarioNome: String,
    produtoPreenchido: ProdutoPreco? = null,
    estadoPre: String,
    cidadePre: String,
    onVoltar: () -> Unit,
    onSalvar: () -> Unit
) {
    val context = LocalContext.current
    val db = FirebaseFirestore.getInstance()
    val focusManager = LocalFocusManager.current

    var codigoBarras by remember { mutableStateOf("") }
    var nome by remember { mutableStateOf("") }
    var preco by remember { mutableStateOf(TextFieldValue("0,00", TextRange(4))) }
    var comentario by remember { mutableStateOf("") }
    var metodo by remember { mutableStateOf("MANUAL") }
    var fotoBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var mostrarOpcoesFoto by remember { mutableStateOf(false) }

    var mercado by remember { mutableStateOf("") }
    val listaMercados = remember { mutableStateListOf("Cooper", "Komprão", "Fort Atacadista", "Angeloni", "Giassi", "Rancho Bom", "Outro") }
    var mercadoExpandido by remember { mutableStateOf(false) }
    val mercadosFiltrados = remember(mercado, listaMercados) {
        if (mercado.isBlank()) listaMercados else listaMercados.filter { it.contains(mercado, ignoreCase = true) }
    }

    val scanner = GmsBarcodeScanning.getClient(context)
    val scrollState = rememberScrollState()

    LaunchedEffect(Unit) {
        db.collection("mercados").get().addOnSuccessListener { result ->
            for (document in result) {
                val nomeMercado = document.getString("nome")
                if (nomeMercado != null && !listaMercados.contains(nomeMercado)) {
                    listaMercados.add(nomeMercado)
                }
            }
            val ordenada = listaMercados.sorted()
            listaMercados.clear()
            listaMercados.addAll(ordenada)
        }
    }

    var temPermissaoCamera by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val launcherPermissao = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { concedida -> temPermissaoCamera = concedida }
    val launcherCamera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap -> if (bitmap != null) fotoBitmap = bitmap }
    val launcherGaleria = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            try { val stream = context.contentResolver.openInputStream(uri); fotoBitmap = BitmapFactory.decodeStream(stream) } catch (e: Exception) {}
        }
    }

    LaunchedEffect(produtoPreenchido) {
        if (produtoPreenchido != null) {
            nome = produtoPreenchido.nomeProduto
            codigoBarras = produtoPreenchido.codigoBarras
            val precoFormatado = String.format(Locale("pt", "BR"), "%.2f", produtoPreenchido.valor)
            preco = TextFieldValue(precoFormatado, TextRange(precoFormatado.length))
            if (produtoPreenchido.fotoBase64.isNotEmpty()) fotoBitmap = stringParaBitmap(produtoPreenchido.fotoBase64)
            mercado = produtoPreenchido.mercado
        }
    }

    fun buscarProdutoPorCodigo(codigo: String) {
        if (codigo.isEmpty()) return
        Toast.makeText(context, "Buscando...", Toast.LENGTH_SHORT).show()
        db.collection("produtos_unicos").document(codigo).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    nome = document.getString("nome") ?: ""
                    Toast.makeText(context, "Produto encontrado: $nome", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Código novo.", Toast.LENGTH_SHORT).show()
                }
            }
    }

    if (mostrarOpcoesFoto) {
        AlertDialog(
            onDismissRequest = { mostrarOpcoesFoto = false },
            title = { Text("Adicionar Imagem") },
            text = { Text("Escolha a origem da imagem:") },
            confirmButton = { Button(onClick = { mostrarOpcoesFoto = false; if (temPermissaoCamera) launcherCamera.launch(null) else launcherPermissao.launch(Manifest.permission.CAMERA) }) { Icon(Icons.Default.CameraAlt, null); Spacer(Modifier.width(8.dp)); Text("Câmera") } },
            dismissButton = { Button(onClick = { mostrarOpcoesFoto = false; launcherGaleria.launch("image/*") }) { Icon(Icons.Default.Image, null); Spacer(Modifier.width(8.dp)); Text("Galeria") } }
        )
    }

    Column(modifier = Modifier.padding(16.dp).fillMaxSize().verticalScroll(scrollState).imePadding()) {
        Text(if(produtoPreenchido != null) "Novo Preço" else "Cadastrar", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(16.dp))

        if (produtoPreenchido == null) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Image(painter = painterResource(id = R.drawable.scancode), contentDescription = "Scan", modifier = Modifier.weight(1f).height(100.dp).clickable { if (temPermissaoCamera) { scanner.startScan().addOnSuccessListener { barcode -> val rawValue = barcode.rawValue; if (rawValue != null && !rawValue.contains("/")) { codigoBarras = rawValue; metodo = "SCANNER"; buscarProdutoPorCodigo(rawValue) } else { Toast.makeText(context, "Escaneie apenas códigos de barras!", Toast.LENGTH_LONG).show() } } } else { launcherPermissao.launch(Manifest.permission.CAMERA) } }, contentScale = ContentScale.Fit)
                Button(onClick = { mostrarOpcoesFoto = true }, modifier = Modifier.weight(1f).height(50.dp), contentPadding = PaddingValues(4.dp)) { Icon(Icons.Default.CameraAlt, null); Text("Foto/Galeria", fontSize = 11.sp, modifier = Modifier.padding(start = 4.dp)) }
            }
            Spacer(modifier = Modifier.height(10.dp))
            if (fotoBitmap != null) { Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) { Image(bitmap = fotoBitmap!!.asImageBitmap(), contentDescription = "Preview") }; Spacer(modifier = Modifier.height(10.dp)) }
            OutlinedTextField(value = codigoBarras, onValueChange = { codigoBarras = it }, label = { Text("Ou digite o Código") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { buscarProdutoPorCodigo(codigoBarras); focusManager.clearFocus() }), trailingIcon = { IconButton(onClick = { buscarProdutoPorCodigo(codigoBarras); focusManager.clearFocus() }) { Icon(Icons.Default.Search, contentDescription = "Buscar") } })
            Spacer(modifier = Modifier.height(16.dp))
        } else { if(codigoBarras.isNotEmpty()) { OutlinedTextField(value = codigoBarras, onValueChange = {}, label = { Text("Código de Barras") }, enabled = false, modifier = Modifier.fillMaxWidth()); Spacer(modifier = Modifier.height(16.dp)) } }

        OutlinedTextField(value = nome, onValueChange = { nome = it }, label = { Text("Nome do Produto") }, modifier = Modifier.fillMaxWidth(), enabled = (produtoPreenchido == null), singleLine = true, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next))

        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            OutlinedTextField(value = mercado, onValueChange = { mercado = it; mercadoExpandido = true }, label = { Text("Mercado") }, trailingIcon = { Icon(Icons.Default.ArrowDropDown, null, Modifier.clickable { mercadoExpandido = !mercadoExpandido }) }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next))
            if (mercadoExpandido && mercadosFiltrados.isNotEmpty()) { DropdownMenu(expanded = mercadoExpandido, onDismissRequest = { mercadoExpandido = false }, modifier = Modifier.fillMaxWidth(0.9f), properties = PopupProperties(focusable = false)) { mercadosFiltrados.forEach { m -> DropdownMenuItem(text = { Text(m) }, onClick = { mercado = m; mercadoExpandido = false }) } } }
        }

        OutlinedTextField(value = preco, onValueChange = { novoValor -> val digits = novoValor.text.filter { it.isDigit() }; if (digits.length <= 10) { val valorRaw = if (digits.isEmpty()) 0L else digits.toLong(); val valorDouble = valorRaw / 100.0; val formatado = String.format(Locale("pt", "BR"), "%.2f", valorDouble); preco = TextFieldValue(formatado, TextRange(formatado.length)) } }, label = { Text("Preço (R$)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next), modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(value = comentario, onValueChange = { comentario = it }, label = { Text("Comentário (Opcional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }))

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = {
                if (temOfensa(nome) || temOfensa(comentario)) { Toast.makeText(context, "Conteúdo impróprio detectado!", Toast.LENGTH_LONG).show(); return@Button }
                val precoFinal = preco.text.replace(".", "").replace(",", ".").toDoubleOrNull()
                if (nome.isNotEmpty() && precoFinal != null && mercado.isNotEmpty() && precoFinal > 0) {
                    if (!listaMercados.contains(mercado)) db.collection("mercados").add(hashMapOf("nome" to mercado))
                    val fotoString = if (fotoBitmap != null) bitmapParaString(fotoBitmap!!) else ""
                    val listaComents = if (comentario.isNotEmpty()) listOf("$usuarioNome: $comentario") else emptyList()

                    // ATENÇÃO: Salvando campo "nomePesquisa" em minúsculas
                    val ofertaNova = ProdutoPreco(
                        codigoBarras = codigoBarras,
                        nomeProduto = nome,
                        nomePesquisa = nome.lowercase(), // <--- AQUI ESTÁ A MÁGICA
                        valor = precoFinal,
                        mercado = mercado,
                        cidade = cidadePre,
                        estado = estadoPre,
                        metodoEntrada = if(codigoBarras.isNotEmpty() && metodo == "MANUAL") "DIGITADO" else metodo,
                        comentario = comentario,
                        chatComentarios = listaComents,
                        usuarioId = usuarioNome,
                        fotoBase64 = fotoString,
                        data = Date()
                    )

                    val query = if (codigoBarras.isNotEmpty()) db.collection("ofertas").whereEqualTo("codigoBarras", codigoBarras).whereEqualTo("mercado", mercado) else db.collection("ofertas").whereEqualTo("nomeProduto", nome).whereEqualTo("mercado", mercado)
                    query.get().addOnSuccessListener { snapshot ->
                        if (!snapshot.isEmpty) {
                            val idExistente = snapshot.documents[0].id
                            val dadosAntigos = snapshot.documents[0].toObject(ProdutoPreco::class.java)
                            val fotoFinal = if(fotoString.isNotEmpty()) fotoString else dadosAntigos?.fotoBase64 ?: ""
                            val comentsFinais = (dadosAntigos?.chatComentarios ?: emptyList()) + listaComents
                            // Atualiza mantendo o minúsculo
                            val ofertaAtualizada = ofertaNova.copy(fotoBase64 = fotoFinal, chatComentarios = comentsFinais)
                            db.collection("ofertas").document(idExistente).set(ofertaAtualizada)
                            Toast.makeText(context, "Preço ATUALIZADO!", Toast.LENGTH_SHORT).show()
                        } else {
                            db.collection("ofertas").add(ofertaNova)
                            Toast.makeText(context, "Preço CADASTRADO!", Toast.LENGTH_SHORT).show()
                        }
                    }
                    if (codigoBarras.isNotEmpty()) { val dadosProduto = hashMapOf("nome" to nome); db.collection("produtos_unicos").document(codigoBarras).set(dadosProduto) }
                    onSalvar()
                } else { Toast.makeText(context, "Preencha os dados obrigatórios!", Toast.LENGTH_SHORT).show() }
            },
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) { Text("PUBLICAR PREÇO") }
        TextButton(onClick = onVoltar, modifier = Modifier.fillMaxWidth()) { Text("Cancelar") }
    }
}