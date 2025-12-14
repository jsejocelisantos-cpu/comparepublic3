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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.ui.res.painterResource // IMPORTANTE: Import para carregar imagem
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.compare.R // IMPORTANTE: Import dos recursos (R) para achar o scancode
import com.example.compare.model.ProdutoPreco
import com.example.compare.utils.bitmapParaString
import com.example.compare.utils.stringParaBitmap
import com.example.compare.utils.temOfensa
import com.google.firebase.firestore.FirebaseFirestore
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import java.util.Date

// --- TELA DE EDIÇÃO INTERNA (Usada no diálogo da HOME) ---
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
                    val nova = oferta.copy(nomeProduto = nomeEd, valor = novoPreco, codigoBarras = codEd, fotoBase64 = novaFoto)
                    onSave(nova)
                }
            }) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancelar") } }
    )
}


// --- 6. TELA DE CADASTRO ---
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
    var preco by remember { mutableStateOf("") }
    var comentario by remember { mutableStateOf("") }
    var metodo by remember { mutableStateOf("MANUAL") }
    var fotoBitmap by remember { mutableStateOf<Bitmap?>(null) }

    var mercado by remember { mutableStateOf("") }
    val listaMercados = remember { mutableStateListOf("Cooper", "Komprão", "Fort Atacadista", "Angeloni", "Giassi", "Rancho Bom", "Outro") }
    var mercadoExpandido by remember { mutableStateOf(false) }

    val scanner = GmsBarcodeScanning.getClient(context)

    // CARREGA MERCADOS
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

    var temPermissaoCamera by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val launcherPermissao = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { concedida -> temPermissaoCamera = concedida }
    val launcherCamera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap -> if (bitmap != null) fotoBitmap = bitmap }
    val launcherGaleria = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            try {
                val stream = context.contentResolver.openInputStream(uri)
                fotoBitmap = BitmapFactory.decodeStream(stream)
            } catch (e: Exception) {}
        }
    }

    LaunchedEffect(produtoPreenchido) {
        if (produtoPreenchido != null) {
            nome = produtoPreenchido.nomeProduto
            codigoBarras = produtoPreenchido.codigoBarras
            if (produtoPreenchido.fotoBase64.isNotEmpty()) {
                fotoBitmap = stringParaBitmap(produtoPreenchido.fotoBase64)
            }
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

    Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
        Text(if(produtoPreenchido != null) "Novo Preço" else "Cadastrar", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(16.dp))

        if (produtoPreenchido == null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // --- BOTÃO DE SCAN COM IMAGEM ---
                Button(
                    onClick = {
                        if (temPermissaoCamera) {
                            scanner.startScan().addOnSuccessListener { barcode ->
                                val rawValue = barcode.rawValue
                                if (rawValue != null && !rawValue.contains("/")) {
                                    codigoBarras = rawValue
                                    metodo = "SCANNER"
                                    buscarProdutoPorCodigo(rawValue)
                                } else {
                                    Toast.makeText(context, "Escaneie apenas códigos de barras!", Toast.LENGTH_LONG).show()
                                }
                            }
                        } else { launcherPermissao.launch(Manifest.permission.CAMERA) }
                    },
                    modifier = Modifier.weight(1f).height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent), // Fundo transparente para a imagem aparecer bem
                    contentPadding = PaddingValues(0.dp) // Sem bordas internas
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.scancode), // AQUI ESTÁ A IMAGEM!
                        contentDescription = "Scan",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }

                Button(
                    onClick = {
                        if (temPermissaoCamera) launcherCamera.launch(null)
                        else launcherPermissao.launch(Manifest.permission.CAMERA)
                    },
                    modifier = Modifier.weight(1f).height(50.dp),
                    contentPadding = PaddingValues(4.dp)
                ) {
                    Icon(Icons.Default.CameraAlt, null)
                    Text("Foto", fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp))
                }
                Button(
                    onClick = { launcherGaleria.launch("image/*") },
                    modifier = Modifier.weight(1f).height(50.dp),
                    contentPadding = PaddingValues(4.dp)
                ) {
                    Icon(Icons.Default.Image, null)
                    Text("Galeria", fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp))
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (fotoBitmap != null) {
                Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                    Image(bitmap = fotoBitmap!!.asImageBitmap(), contentDescription = "Preview")
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            OutlinedTextField(
                value = codigoBarras,
                onValueChange = { codigoBarras = it },
                label = { Text("Ou digite o Código") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    buscarProdutoPorCodigo(codigoBarras)
                    focusManager.clearFocus()
                }),
                trailingIcon = {
                    IconButton(onClick = {
                        buscarProdutoPorCodigo(codigoBarras)
                        focusManager.clearFocus()
                    }) {
                        Icon(Icons.Default.Search, contentDescription = "Buscar")
                    }
                }
            )
            Spacer(modifier = Modifier.height(16.dp))
        } else {
            if(codigoBarras.isNotEmpty()) {
                OutlinedTextField(
                    value = codigoBarras,
                    onValueChange = {},
                    label = { Text("Código de Barras") },
                    enabled = false,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        OutlinedTextField(
            value = nome,
            onValueChange = { nome = it },
            label = { Text("Nome do Produto") },
            modifier = Modifier.fillMaxWidth(),
            enabled = (produtoPreenchido == null),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
        )

        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            OutlinedTextField(
                value = mercado,
                onValueChange = { mercado = it },
                label = { Text("Mercado") },
                trailingIcon = { Icon(Icons.Default.Add, null, Modifier.clickable { mercadoExpandido = true }) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
            )
            DropdownMenu(expanded = mercadoExpandido, onDismissRequest = { mercadoExpandido = false }) {
                listaMercados.forEach { m ->
                    DropdownMenuItem(text = { Text(m) }, onClick = { mercado = m; mercadoExpandido = false })
                }
            }
        }

        OutlinedTextField(
            value = preco,
            onValueChange = { preco = it },
            label = { Text("Preço (R$)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        OutlinedTextField(
            value = comentario,
            onValueChange = { comentario = it },
            label = { Text("Comentário (Opcional)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
        )

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = {
                if (temOfensa(nome) || temOfensa(comentario)) {
                    Toast.makeText(context, "Conteúdo impróprio detectado!", Toast.LENGTH_LONG).show()
                    return@Button
                }

                val precoFinal = preco.replace(",", ".").toDoubleOrNull()
                if (nome.isNotEmpty() && precoFinal != null && mercado.isNotEmpty()) {

                    if (!listaMercados.contains(mercado)) {
                        db.collection("mercados").add(hashMapOf("nome" to mercado))
                    }

                    val fotoString = if (fotoBitmap != null) bitmapParaString(fotoBitmap!!) else ""
                    val listaComents = if (comentario.isNotEmpty()) listOf("$usuarioNome: $comentario") else emptyList()

                    val ofertaNova = ProdutoPreco(
                        codigoBarras = codigoBarras,
                        nomeProduto = nome,
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

                    val query = if (codigoBarras.isNotEmpty()) {
                        db.collection("ofertas")
                            .whereEqualTo("codigoBarras", codigoBarras)
                            .whereEqualTo("mercado", mercado)
                    } else {
                        db.collection("ofertas")
                            .whereEqualTo("nomeProduto", nome)
                            .whereEqualTo("mercado", mercado)
                    }

                    query.get().addOnSuccessListener { snapshot ->
                        if (!snapshot.isEmpty) {
                            val idExistente = snapshot.documents[0].id
                            val dadosAntigos = snapshot.documents[0].toObject(ProdutoPreco::class.java)
                            val fotoFinal = if(fotoString.isNotEmpty()) fotoString else dadosAntigos?.fotoBase64 ?: ""
                            val comentsFinais = (dadosAntigos?.chatComentarios ?: emptyList()) + listaComents

                            val ofertaAtualizada = ofertaNova.copy(fotoBase64 = fotoFinal, chatComentarios = comentsFinais)
                            db.collection("ofertas").document(idExistente).set(ofertaAtualizada)
                            Toast.makeText(context, "Preço ATUALIZADO!", Toast.LENGTH_SHORT).show()
                        } else {
                            db.collection("ofertas").add(ofertaNova)
                            Toast.makeText(context, "Preço CADASTRADO!", Toast.LENGTH_SHORT).show()
                        }
                    }

                    if (codigoBarras.isNotEmpty()) {
                        val dadosProduto = hashMapOf("nome" to nome)
                        db.collection("produtos_unicos").document(codigoBarras).set(dadosProduto)
                    }
                    onSalvar()
                } else {
                    Toast.makeText(context, "Preencha os dados obrigatórios!", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            Text("PUBLICAR PREÇO")
        }

        TextButton(onClick = onVoltar, modifier = Modifier.fillMaxWidth()) { Text("Cancelar") }
    }
}