package com.example.cardapp.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.cardapp.ui.AppViewModel
import com.example.cardapp.ui.components.LiquidGlass
import com.example.cardapp.ui.theme.AccentViolet

/** Вход и регистрация по почте и паролю (Firebase Authentication). */
@Composable
fun AuthScreen(vm: AppViewModel, modifier: Modifier = Modifier) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var passwordConfirm by rememberSaveable { mutableStateOf("") }
    var isRegister by rememberSaveable { mutableStateOf(false) }
    val busy by vm.authBusy.collectAsState()
    val error by vm.authError.collectAsState()
    val info by vm.authInfo.collectAsState()

    val passwordsMismatch =
        isRegister && passwordConfirm.isNotEmpty() && password != passwordConfirm
    val canSubmit = !busy && email.isNotBlank() && password.isNotBlank() &&
        (!isRegister || password == passwordConfirm)

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Карточки", style = MaterialTheme.typography.headlineMedium)
            LiquidGlass(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
              Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
              ) {
            Text(
                text =
                    if (isRegister) "Создай аккаунт — слова будут синхронизироваться между твоими устройствами"
                    else "Войди, чтобы слова были на всех твоих устройствах",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Почта") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Пароль") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
            if (isRegister) {
                OutlinedTextField(
                    value = passwordConfirm,
                    onValueChange = { passwordConfirm = it },
                    label = { Text("Повтори пароль") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    isError = passwordsMismatch,
                    supportingText =
                        if (passwordsMismatch) {
                            { Text("Пароли не совпадают") }
                        } else null,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            error?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
            }
            info?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                )
            }
            Button(
                onClick = {
                    if (isRegister) vm.signUp(email, password) else vm.signIn(email, password)
                },
                enabled = canSubmit,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentViolet,
                    contentColor = Color.White,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(if (isRegister) "Создать аккаунт" else "Войти")
                }
            }
            if (!isRegister) {
                TextButton(onClick = { vm.sendPasswordReset(email) }, enabled = !busy) {
                    Text("Забыли пароль?")
                }
            }
            TextButton(onClick = {
                isRegister = !isRegister
                vm.clearAuthError()
            }) {
                Text(
                    if (isRegister) "Уже есть аккаунт? Войти"
                    else "Нет аккаунта? Зарегистрироваться"
                )
            }
              }
            }
        }
    }
}
