package com.example.compare.model

import java.util.Date

// --- 1. MODELO DE DADOS PRINCIPAL ---
data class ProdutoPreco(
    val id: String = "",
    val codigoBarras: String = "",
    val nomeProduto: String = "",
    val nomePesquisa: String = "", // Usado para ordenação ou busca antiga

    // --- NOVO CAMPO: LISTA DE PALAVRAS-CHAVE ---
    // Armazena cada palavra do nome separadamente para busca flexível (ex: "arroz", "tio")
    val palavrasChave: List<String> = emptyList(),

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

// --- 2. DADOS DO MERCADO ---
data class DadosMercado(
    val id: String = "",
    val nome: String = "",
    val endereco: String = "",
    val telefone: String = "",
    val horario: String = "",
    val cidade: String = ""
)

// --- 3. USUÁRIO ---
data class Usuario(
    val nome: String = "",
    val ultimoAcesso: Date = Date()
)

// --- 4. SUPORTE ---
data class MensagemSuporte(
    val id: String = "",
    val usuario: String = "",
    val msg: String = "",
    val data: Date = Date(),
    val resposta: String = "",
    val dataResposta: Date? = null
)

// --- 5. ITEM DA LISTA DE COMPRAS ---
data class ItemLista(
    val id: String = "",
    val usuarioId: String = "",
    val nomeProduto: String = "", // Nome para buscar ofertas
    val codigoBarras: String = "", // Opcional, ajuda na precisão
    val quantidade: Int = 1,
    val comprado: Boolean = false // Checkbox
)