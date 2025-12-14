package com.example.compare.model

import java.util.Date

<<<<<<< HEAD
// --- MODELOS DE DADOS ---

=======
// --- 1. MODELO DE DADOS ---
>>>>>>> 3d41a7eb3184cea72bb4f1555414a807dd43964c
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
<<<<<<< HEAD
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
=======
>>>>>>> 3d41a7eb3184cea72bb4f1555414a807dd43964c
)