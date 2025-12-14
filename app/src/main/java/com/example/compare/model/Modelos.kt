package com.example.compare.model

import java.util.Date

// --- MODELOS DE DADOS ---

data class ProdutoPreco(
    val id: String = "",
    val codigoBarras: String = "",
    val nomeProduto: String = "",
    val valor: Double = 0.0,
    val mercado: String = "",
    val cidade: String = "",
    val estado: String = "",
    val data: Date = Date(),
    val usuarioId: String = "anonimo",
    val metodoEntrada: String = "MANUAL",
    val comentario: String = "",
    val fotoBase64: String = "",
    val chatComentarios: List<String> = emptyList()
)

data class DadosMercado(
    val id: String = "",
    val nome: String = "",
    val endereco: String = "",
    val telefone: String = "",
    val horario: String = ""
)

// --- NOVAS CLASSES PARA O ADMIN ---
data class Usuario(
    val nome: String = "",
    val ultimoAcesso: Date = Date()
)

data class MensagemSuporte(
    val id: String = "",
    val usuario: String = "",
    val msg: String = "",
    val data: Date = Date()
)