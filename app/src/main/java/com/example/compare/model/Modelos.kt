package com.example.compare.model

import java.util.Date

data class ProdutoPreco(
    val id: String = "",
    val usuarioId: String = "",
    val nomeProduto: String = "",
    val nomePesquisa: String = "",
    val palavrasChave: List<String> = emptyList(), // Necessário para a busca
    val mercado: String = "",
    val valor: Double = 0.0,
    val data: Date = Date(),
    val codigoBarras: String = "",
    val fotoBase64: String = "",
    val comentario: String = "",
    val chatComentarios: List<String> = emptyList(),
    val estado: String = "",
    val cidade: String = ""
)

data class ItemLista(
    val id: String = "",
    val usuarioId: String = "",
    val nomeProduto: String = "",
    val codigoBarras: String = "",
    val quantidade: Int = 1,
    val comprado: Boolean = false
)

data class Usuario(
    val id: String = "",
    val nome: String = "",
    val email: String = "",
    val ultimoAcesso: Date = Date(),
    val isAdmin: Boolean = false
)

data class MensagemSuporte(
    val id: String = "",
    val usuario: String = "",
    val msg: String = "",
    val data: Date = Date(),
    val resposta: String = "",
    val dataResposta: Date? = null
)

// ATUALIZADO: Contém todos os campos para evitar erros de "No parameter found"
data class DadosMercado(
    val id: String = "",
    val nome: String = "",
    val endereco: String = "",
    val bairro: String = "",
    val cep: String = "",
    val cidade: String = "",
    val telefone: String = "",
    val horario: String = ""
)