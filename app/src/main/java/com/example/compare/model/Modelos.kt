package com.example.compare.model

import java.util.Date

// --- 1. MODELO DE DADOS PRINCIPAL ---
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

// --- 2. DADOS DO MERCADO (Para o Admin) ---
data class DadosMercado(
    val id: String = "",
    val nome: String = "",
    val endereco: String = "",
    val telefone: String = "",
    val horario: String = ""
)

// --- 3. USUÁRIO (Para o Login salvar acesso) ---
// SE ESTA CLASSE FALTAR, A TELA DE LOGIN DÁ ERRO!
data class Usuario(
    val nome: String = "",
    val ultimoAcesso: Date = Date()
)

// --- 4. SUPORTE (Para o Chat) ---
data class MensagemSuporte(
    val id: String = "",
    val usuario: String = "",
    val msg: String = "",
    val data: Date = Date(),
    val resposta: String = "",
    val dataResposta: Date? = null
)