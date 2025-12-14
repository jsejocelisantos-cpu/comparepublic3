package com.example.compare.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
<<<<<<< HEAD
import com.example.compare.model.Usuario
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Date

=======

// --- 4. TELA DE LOGIN ---
>>>>>>> 3d41a7eb3184cea72bb4f1555414a807dd43964c
@Composable
fun TelaLogin(onLoginSucesso: (String, Boolean) -> Unit) {
    var nome by remember { mutableStateOf("") }
    var senha by remember { mutableStateOf("") }
    var erro by remember { mutableStateOf("") }
    var senhaVisivel by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
<<<<<<< HEAD
    val db = FirebaseFirestore.getInstance() // Para salvar o usuário
=======
>>>>>>> 3d41a7eb3184cea72bb4f1555414a807dd43964c

    val isAdminMode = nome.trim().equals("admin", ignoreCase = true)

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.ShoppingBag,
            contentDescription = "Logo App",
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(20.dp))
        Text("Compare", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(40.dp))

        OutlinedTextField(
            value = nome,
            onValueChange = { nome = it },
            label = { Text("Seu Nome") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
        )

        if (isAdminMode) {
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
                value = senha,
                onValueChange = { senha = it },
                label = { Text("Senha Administrativa") },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (senhaVisivel) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                trailingIcon = {
                    val imagem = if (senhaVisivel) Icons.Default.Visibility else Icons.Default.VisibilityOff
                    IconButton(onClick = { senhaVisivel = !senhaVisivel }) {
                        Icon(imageVector = imagem, contentDescription = null)
                    }
                }
            )
        }

        if (erro.isNotEmpty()) {
            Text(erro, color = Color.Red, modifier = Modifier.padding(top = 8.dp))
        }

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = {
                if (isAdminMode) {
                    if (senha == "Jaragu@123") {
                        onLoginSucesso("Administrador", true)
                    } else {
                        erro = "Senha incorreta!"
                    }
                } else if (nome.isNotBlank()) {
<<<<<<< HEAD
                    // --- SALVA O USUÁRIO NO BANCO DE DADOS ---
                    val usuarioNovo = Usuario(nome = nome, ultimoAcesso = Date())
                    db.collection("usuarios").document(nome).set(usuarioNovo)

=======
>>>>>>> 3d41a7eb3184cea72bb4f1555414a807dd43964c
                    onLoginSucesso(nome, false)
                } else {
                    erro = "Digite seu nome para entrar."
                }
            },
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            Text("ENTRAR")
        }
    }
}