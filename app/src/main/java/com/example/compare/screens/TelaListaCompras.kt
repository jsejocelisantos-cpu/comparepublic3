package com.example.compare.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.compare.model.ItemLista
import com.example.compare.model.ProdutoPreco
import com.google.firebase.firestore.FirebaseFirestore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelaListaCompras(
    usuarioLogado: String,
    cidadeAtual: String,
    onVoltar: () -> Unit
) {
    val db = FirebaseFirestore.getInstance()
    val context = LocalContext.current

    // Estados da Lista
    var itensLista by remember { mutableStateOf<List<ItemLista>>(emptyList()) }
    var ofertasEncontradas by remember { mutableStateOf<List<ProdutoPreco>>(emptyList()) }
    var carregando by remember { mutableStateOf(true) }

    // Estados de Comparação
    var mercadoSelecionado by remember { mutableStateOf("Melhor Preço") }
    var listaMercadosDisponiveis by remember { mutableStateOf<List<String>>(emptyList()) }
    var expandirOpcoesMercado by remember { mutableStateOf(false) }

    // 1. Carrega os itens que o usuário adicionou na lista
    LaunchedEffect(usuarioLogado) {
        db.collection("lista_compras")
            .whereEqualTo("usuarioId", usuarioLogado)
            .addSnapshotListener { res, _ ->
                if (res != null) {
                    itensLista = res.documents.map { it.toObject(ItemLista::class.java)!!.copy(id = it.id) }
                    carregando = false
                }
            }
    }

    // 2. Sempre que a lista mudar, busca as ofertas (preços) desses produtos na cidade atual
    LaunchedEffect(itensLista, cidadeAtual) {
        if (itensLista.isNotEmpty()) {
            val nomesProdutos = itensLista.map { it.nomeProduto }
            // Nota: Firestore "in" limita a 10 itens. Para produção, faríamos diferente.
            // Aqui vamos buscar tudo e filtrar na memória para simplificar o protótipo.
            db.collection("ofertas")
                .whereEqualTo("cidade", cidadeAtual)
                .get()
                .addOnSuccessListener { result ->
                    val todasOfertas = result.documents.map { it.toObject(ProdutoPreco::class.java)!! }
                    // Filtra apenas ofertas dos produtos que estão na lista (por nome ou código)
                    ofertasEncontradas = todasOfertas.filter { oferta ->
                        itensLista.any { item ->
                            item.nomeProduto.equals(oferta.nomeProduto, ignoreCase = true) ||
                                    (item.codigoBarras.isNotEmpty() && item.codigoBarras == oferta.codigoBarras)
                        }
                    }
                    // Descobre quais mercados têm esses produtos
                    listaMercadosDisponiveis = ofertasEncontradas.map { it.mercado }.distinct().sorted()
                }
        }
    }

    // Função para calcular o preço de um item específico no mercado selecionado
    fun calcularPrecoItem(item: ItemLista): Double {
        val ofertasDoProduto = ofertasEncontradas.filter {
            it.nomeProduto.equals(item.nomeProduto, ignoreCase = true) ||
                    (it.codigoBarras.isNotEmpty() && it.codigoBarras == item.codigoBarras)
        }

        if (ofertasDoProduto.isEmpty()) return 0.0

        return if (mercadoSelecionado == "Melhor Preço") {
            // Pega o menor valor encontrado em qualquer mercado
            ofertasDoProduto.minOf { it.valor }
        } else {
            // Tenta achar o preço no mercado selecionado
            val ofertaNoMercado = ofertasDoProduto.find { it.mercado == mercadoSelecionado }
            // Se não tiver nesse mercado, retorna 0 ou poderia retornar uma média (opcional)
            ofertaNoMercado?.valor ?: 0.0
        }
    }

    // Calcula o Total Geral
    val totalCarrinho = itensLista.sumOf { item ->
        if (item.comprado) 0.0 else calcularPrecoItem(item) * item.quantidade
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Carrinho Inteligente") },
                navigationIcon = { IconButton(onClick = onVoltar) { Icon(Icons.Default.ArrowBack, "Voltar") } }
            )
        },
        bottomBar = {
            // Barra de Total
            Surface(tonalElevation = 8.dp, color = MaterialTheme.colorScheme.surfaceVariant) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Seletor de Mercado
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { expandirOpcoesMercado = true }) {
                        Icon(Icons.Default.Store, null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Simular compra no:", fontSize = 12.sp)
                            Text(mercadoSelecionado, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        Icon(Icons.Default.ArrowDropDown, null)
                    }

                    DropdownMenu(expanded = expandirOpcoesMercado, onDismissRequest = { expandirOpcoesMercado = false }) {
                        DropdownMenuItem(text = { Text("Melhor Preço (Mix)") }, onClick = { mercadoSelecionado = "Melhor Preço"; expandirOpcoesMercado = false })
                        listaMercadosDisponiveis.forEach { mercado ->
                            DropdownMenuItem(text = { Text(mercado) }, onClick = { mercadoSelecionado = mercado; expandirOpcoesMercado = false })
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Total Estimado:", fontSize = 18.sp)
                        Text("R$ ${String.format("%.2f", totalCarrinho)}", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = if(totalCarrinho > 0) Color(0xFF00C853) else Color.Gray)
                    }
                    if (mercadoSelecionado != "Melhor Preço" && totalCarrinho == 0.0 && itensLista.any { !it.comprado }) {
                        Text("Este mercado não possui os itens da lista.", fontSize = 12.sp, color = Color.Red)
                    }
                }
            }
        }
    ) { padding ->
        if (itensLista.isEmpty()) {
            Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Sua lista está vazia.\nAdicione produtos na tela inicial!", textAlign = androidx.compose.ui.text.style.TextAlign.Center, color = Color.Gray)
            }
        } else {
            LazyColumn(modifier = Modifier.padding(padding)) {
                items(itensLista) { item ->
                    val precoUnitario = calcularPrecoItem(item)

                    Card(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = if (item.comprado) Color.Gray.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = item.comprado,
                                onCheckedChange = { isChecked ->
                                    db.collection("lista_compras").document(item.id).update("comprado", isChecked)
                                }
                            )

                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.nomeProduto, fontWeight = FontWeight.Bold, style = androidx.compose.ui.text.TextStyle(textDecoration = if (item.comprado) androidx.compose.ui.text.style.TextDecoration.LineThrough else null))
                                if (precoUnitario > 0) {
                                    Text("R$ ${String.format("%.2f", precoUnitario)} un.", fontSize = 12.sp, color = Color.Gray)
                                } else {
                                    Text("Preço indisponível neste mercado", fontSize = 12.sp, color = Color.Red)
                                }
                            }

                            // Controles de Quantidade
                            IconButton(onClick = {
                                if (item.quantidade > 1) {
                                    db.collection("lista_compras").document(item.id).update("quantidade", item.quantidade - 1)
                                } else {
                                    // Remove se chegar a 0
                                    db.collection("lista_compras").document(item.id).delete()
                                }
                            }, modifier = Modifier.size(30.dp)) { Icon(Icons.Default.Remove, null) }

                            Text("${item.quantidade}", modifier = Modifier.padding(horizontal = 8.dp), fontWeight = FontWeight.Bold)

                            IconButton(onClick = {
                                db.collection("lista_compras").document(item.id).update("quantidade", item.quantidade + 1)
                            }, modifier = Modifier.size(30.dp)) { Icon(Icons.Default.Add, null) }
                        }
                    }
                }
                item { Spacer(modifier = Modifier.height(80.dp)) } // Espaço para a barra inferior não cobrir o último item
            }
        }
    }
}

class TelaListaCompras {
}