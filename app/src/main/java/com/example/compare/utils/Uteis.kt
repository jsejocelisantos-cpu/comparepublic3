package com.example.compare.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// --- FUNÇÕES ÚTEIS ---

fun temOfensa(texto: String): Boolean {
    val listaProibida = listOf("droga", "burro", "idiota", "merda", "bosta", "puta", "vagabundo", "imbecil", "caralho", "porra", "otario", "cu", "foder", "arrombado")
    val textoNormalizado = Normalizer.normalize(texto, Normalizer.Form.NFD).replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "").lowercase(Locale.getDefault())
    for (palavra in listaProibida) { if (textoNormalizado.contains(palavra)) return true }
    return false
}

fun formatarData(data: Date): String {
    val sdf = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
    return sdf.format(data)
}

fun bitmapParaString(bitmap: Bitmap): String {
    val stream = ByteArrayOutputStream()
    val resized = Bitmap.createScaledBitmap(bitmap, 600, (600.0 * bitmap.height / bitmap.width).toInt(), true)
    resized.compress(Bitmap.CompressFormat.JPEG, 60, stream)
    val bytes = stream.toByteArray()
    return Base64.encodeToString(bytes, Base64.DEFAULT)
}

fun stringParaBitmap(encodedString: String): Bitmap? {
    return try {
        val bytes = Base64.decode(encodedString, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    } catch (e: Exception) { null }
}

// --- DADOS DE LOCALIZAÇÃO (Lista gigante movida para cá) ---
val dadosBrasil = mapOf(
    "SC" to listOf("Abdon Batista", "Abelardo Luz", "Agrolândia", "Agronômica", "Água Doce", "Águas de Chapecó", "Águas Frias", "Águas Mornas", "Alfredo Wagner", "Alto Bela Vista", "Anchieta", "Angelina", "Anita Garibaldi", "Anitápolis", "Antônio Carlos", "Apiúna", "Arabutã", "Araquari", "Araranguá", "Armazém", "Arroio Trinta", "Arvoredo", "Ascurra", "Atalanta", "Aurora", "Balneário Arroio do Silva", "Balneário Barra do Sul", "Balneário Camboriú", "Balneário Gaivota", "Balneário Piçarras", "Balneário Rincão", "Bandeirante", "Barra Bonita", "Barra Velha", "Bela Vista do Toldo", "Belmonte", "Benedito Novo", "Biguaçu", "Blumenau", "Bocaina do Sul", "Bom Jardim da Serra", "Bom Jesus", "Bom Jesus do Oeste", "Bom Retiro", "Bombinhas", "Botuverá", "Braço do Norte", "Braço do Trombudo", "Brunópolis", "Brusque", "Caçador", "Caibi", "Calmon", "Camboriú", "Campo Alegre", "Campo Belo do Sul", "Campo Erê", "Campos Novos", "Canelinha", "Canoinhas", "Capão Alto", "Capinzal", "Capivari de Baixo", "Catanduvas", "Caxambu do Sul", "Celso Ramos", "Cerro Negro", "Chapadão do Lageado", "Chapecó", "Cocal do Sul", "Concórdia", "Cordilheira Alta", "Coronel Freitas", "Coronel Martins", "Correia Pinto", "Corupá", "Criciúma", "Cunha Porã", "Cunhataí", "Curitibanos", "Descanso", "Dionísio Cerqueira", "Dona Emma", "Doutor Pedrinho", "Entre Rios", "Ermo", "Erval Velho", "Faxinal dos Guedes", "Flor do Sertão", "Florianópolis", "Formosa do Sul", "Forquilhinha", "Fraiburgo", "Frei Rogério", "Galvão", "Garopaba", "Garuva", "Gaspar", "Governador Celso Ramos", "Grão-Pará", "Gravatal", "Guabiruba", "Guaraciaba", "Guaramirim", "Guarujá do Sul", "Guatambu", "Herval d'Oeste", "Ibiam", "Ibicaré", "Ibirama", "Içara", "Ilhota", "Imaruí", "Imbituba", "Imbuia", "Indaial", "Iomerê", "Ipira", "Iporã do Oeste", "Ipuaçu", "Ipumirim", "Iraceminha", "Irani", "Irati", "Irineópolis", "Itá", "Itaiópolis", "Itajaí", "Itapema", "Itapiranga", "Itapoá", "Ituporanga", "Jaborá", "Jacinto Machado", "Jaguaruna", "Jaraguá do Sul", "Jardinópolis", "Joaçaba", "Joinville", "José Boiteux", "Jupiá", "Lacerdópolis", "Lages", "Laguna", "Lajeado Grande", "Laurentino", "Lauro Müller", "Lebon Régis", "Leoberto Leal", "Lindóia do Sul", "Lontras", "Luiz Alves", "Luzerna", "Macieira", "Mafra", "Major Gercino", "Major Vieira", "Maracajá", "Maravilha", "Marema", "Massaranduba", "Matos Costa", "Meleiro", "Mirim Doce", "Modelo", "Mondaí", "Monte Carlo", "Monte Castelo", "Morro da Fumaça", "Morro Grande", "Navegantes", "Nova Erechim", "Nova Itaberaba", "Nova Trento", "Nova Veneza", "Novo Horizonte", "Orleans", "Otacílio Costa", "Ouro", "Ouro Verde", "Paial", "Painel", "Palhoça", "Palma Sola", "Palmeira", "Palmitos", "Papanduva", "Paraíso", "Passo de Torres", "Passos Maia", "Paulo Lopes", "Pedras Grandes", "Penha", "Peritiba", "Pescaria Brava", "Petrolândia", "Pinhalzinho", "Pinheiro Preto", "Piratuba", "Planalto Alegre", "Pomerode", "Ponte Alta", "Ponte Alta do Norte", "Ponte Serrada", "Porto Belo", "Porto União", "Pouso Redondo", "Praia Grande", "Presidente Castello Branco", "Presidente Getúlio", "Presidente Nereu", "Princesa", "Quilombo", "Rancho Queimado", "Rio das Antas", "Rio do Campo", "Rio do Oeste", "Rio do Sul", "Rio dos Cedros", "Rio Fortuna", "Rio Negrinho", "Rio Rufino", "Riqueza", "Rodeio", "Romelândia", "Salete", "Saltinho", "Salto Veloso", "Sangão", "Santa Cecília", "Santa Helena", "Santa Rosa de Lima", "Santa Rosa do Sul", "Santa Terezinha", "Santa Terezinha do Progresso", "Santiago do Sul", "Santo Amaro da Imperatriz", "São Bento do Sul", "São Bernardino", "São Bonifácio", "São Carlos", "São Cristóvão do Sul", "São Domingos", "São Francisco do Sul", "São João Batista", "São João do Itaperiú", "São João do Oeste", "São João do Sul", "São Joaquim", "São José", "São José do Cedro", "São José do Cerrito", "São Lourenço do Oeste", "São Ludgero", "São Martinho", "São Miguel da Boa Vista", "São Miguel do Oeste", "São Pedro de Alcântara", "Saudades", "Schroeder", "Seara", "Serra Alta", "Siderópolis", "Sombrio", "Sul Brasil", "Taió", "Tangará", "Tigrinhos", "Tijucas", "Timbé do Sul", "Timbó", "Timbó Grande", "Três Barras", "Treviso", "Treze de Maio", "Treze Tílias", "Trombudo Central", "Tubarão", "Tunápolis", "Turvo", "União do Oeste", "Urubici", "Urupema", "Urussanga", "Vargeão", "Vargem", "Vargem Bonita", "Vidal Ramos", "Videira", "Vitor Meireles", "Witmarsum", "Xanxerê", "Xavantina", "Xaxim", "Zortéa"),
    "AC" to listOf("Rio Branco", "Cruzeiro do Sul"),
    "AL" to listOf("Maceió", "Arapiraca"),
    "AP" to listOf("Macapá", "Santana"),
    "AM" to listOf("Manaus", "Parintins"),
    "BA" to listOf("Salvador", "Feira de Santana"),
    "CE" to listOf("Fortaleza", "Caucaia"),
    "DF" to listOf("Brasília", "Ceilândia"),
    "ES" to listOf("Vitória", "Vila Velha"),
    "GO" to listOf("Goiânia", "Aparecida de Goiânia"),
    "MA" to listOf("São Luís", "Imperatriz"),
    "MT" to listOf("Cuiabá", "Várzea Grande"),
    "MS" to listOf("Campo Grande", "Dourados"),
    "MG" to listOf("Belo Horizonte", "Uberlândia"),
    "PA" to listOf("Belém", "Ananindeua"),
    "PB" to listOf("João Pessoa", "Campina Grande"),
    "PR" to listOf("Curitiba", "Londrina"),
    "PE" to listOf("Recife", "Jaboatão"),
    "PI" to listOf("Teresina", "Parnaíba"),
    "RJ" to listOf("Rio de Janeiro", "São Gonçalo"),
    "RN" to listOf("Natal", "Mossoró"),
    "RS" to listOf("Porto Alegre", "Caxias do Sul"),
    "RO" to listOf("Porto Velho", "Ji-Paraná"),
    "RR" to listOf("Boa Vista", "Rorainópolis"),
    "SP" to listOf("São Paulo", "Campinas"),
    "SE" to listOf("Aracaju", "Socorro"),
    "TO" to listOf("Palmas", "Araguaína")
)