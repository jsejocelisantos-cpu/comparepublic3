package com.example.compare.screens


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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.zIndex
import com.example.compare.R
import com.example.compare.model.ProdutoPreco
import com.example.compare.utils.formatarData
import com.example.compare.utils.stringParaBitmap

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeTopBar(
    cidadeAtual: String,
    isAdmin: Boolean,
    onIrListaCompras: () -> Unit,
    onIrAdmin: () -> Unit,
    onMostrarFiltro: () -> Unit,
    onMostrarSobre: () -> Unit,
    onMostrarSuporte: () -> Unit,
    onSair: () -> Unit
) {
    var menuExpandido by remember { mutableStateOf(false) }

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
                DropdownMenuItem(text = { Text("Alterar Cidade") }, leadingIcon = { Icon(Icons.Default.LocationOn, null) }, onClick = { menuExpandido = false; onMostrarFiltro() })
                Divider()
                if (isAdmin) DropdownMenuItem(text = { Text("Painel Admin") }, onClick = { menuExpandido = false; onIrAdmin() })
                DropdownMenuItem(text = { Text("Sobre o App") }, onClick = { menuExpandido = false; onMostrarSobre() })
                DropdownMenuItem(text = { Text("Suporte") }, onClick = { menuExpandido = false; onMostrarSuporte() })
                Divider()
                DropdownMenuItem(text = { Text("Sair", color = Color.Red) }, onClick = { menuExpandido = false; onSair() })
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeContent(
    paddingValues: PaddingValues,
    todosProdutos: List<ProdutoPreco>,
    isRefreshing: Boolean,
    textoBusca: String,
    sugestoesBusca: List<String>,
    expandirSugestoes: Boolean,
    temMais: Boolean,
    carregandoMais: Boolean,
    carregandoDetalhes: Boolean,
    onRefresh: () -> Unit,
    onSearchChange: (String) -> Unit,
    onSearchSubmit: () -> Unit,
    onSugestaoClick: (String) -> Unit,
    onScanClick: () -> Unit,
    onCarregarMais: () -> Unit,
    onCardClick: (ProdutoPreco) -> Unit,
    onEditClick: (ProdutoPreco) -> Unit,
    onCartClick: (ProdutoPreco) -> Unit
) {
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier.padding(paddingValues).fillMaxSize()
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column {
                // BARRA DE BUSCA
                Box(modifier = Modifier.fillMaxWidth().padding(8.dp).zIndex(1f)) {
                    OutlinedTextField(
                        value = textoBusca, onValueChange = onSearchChange,
                        label = { Text("Buscar produto...") },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        trailingIcon = {
                            Image(
                                painter = painterResource(id = R.drawable.scancode), contentDescription = "Scan", contentScale = ContentScale.Fit,
                                modifier = Modifier.size(50.dp).padding(end = 8.dp).clip(RoundedCornerShape(4.dp)).clickable { onScanClick() }
                            )
                        },
                        modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { onSearchSubmit() })
                    )
                    DropdownMenu(
                        expanded = expandirSugestoes, onDismissRequest = { }, modifier = Modifier.fillMaxWidth(0.95f), properties = PopupProperties(focusable = false)
                    ) {
                        sugestoesBusca.forEach { nome -> DropdownMenuItem(text = { Text(nome) }, onClick = { onSugestaoClick(nome) }) }
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
                            Card(modifier = Modifier.padding(8.dp).fillMaxWidth().clickable { onCardClick(melhorOferta) }, elevation = CardDefaults.cardElevation(4.dp)) {
                                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    val ofertaComFoto = listaDoGrupo.firstOrNull { it.fotoBase64.isNotEmpty() }
                                    if (ofertaComFoto != null) {
                                        val bitmap = stringParaBitmap(ofertaComFoto.fotoBase64)
                                        if (bitmap != null) {
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
                                            Text(text = "Sem ofertas cadastradas", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                        }
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        if (melhorOferta.valor > 0) {
                                            Text("R$ ${String.format("%.2f", melhorOferta.valor)}", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                            Text(text = melhorOferta.mercado, style = MaterialTheme.typography.bodyMedium, color = Color(0xFF03A9F4))
                                        } else {
                                            Text("Catálogo", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                                        }
                                        Row {
                                            IconButton(onClick = { onEditClick(melhorOferta) }, modifier = Modifier.size(30.dp)) { Icon(Icons.Default.Edit, "Editar", tint = MaterialTheme.colorScheme.primary) }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            IconButton(onClick = { onCartClick(melhorOferta) }, modifier = Modifier.size(30.dp)) { Icon(Icons.Default.AddShoppingCart, "Add", tint = MaterialTheme.colorScheme.secondary) }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    item { if (temMais && !carregandoMais) { Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { Button(onClick = onCarregarMais) { Text("Carregar mais") } } } }
                }
            }
            if (carregandoDetalhes) Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }
    }
}