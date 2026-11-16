package com.geivia.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rikka.shizuku.Shizuku

private const val GEIVIA_SHIZUKU_REQUEST = 7301

@Composable
internal fun DeviceAccessSetupScreen(onCompleted: () -> Unit) {
    val context = LocalContext.current
    val power = remember(context) { context.getSystemService(Context.POWER_SERVICE) as PowerManager }
    var notifications by remember { mutableStateOf(Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) }
    var battery by remember { mutableStateOf(Build.VERSION.SDK_INT < 23 || power.isIgnoringBatteryOptimizations(context.packageName)) }
    var shizukuRunning by remember { mutableStateOf(false) }
    var shizukuGranted by remember { mutableStateOf(false) }
    var shizukuStatus by remember { mutableStateOf("Verifique se o Shizuku está instalado e iniciado.") }
    fun refreshShizuku() {
        shizukuRunning = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        shizukuGranted = shizukuRunning && runCatching { Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED }.getOrDefault(false)
        shizukuStatus = when { !shizukuRunning -> "Shizuku não está iniciado."; shizukuGranted -> "Shizuku conectado e autorizado para o GEIVIA."; else -> "Shizuku ativo; falta autorizar o GEIVIA." }
    }
    val notificationRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { notifications = it }
    DisposableEffect(Unit) {
        val listener = Shizuku.OnRequestPermissionResultListener { requestCode, result ->
            if (requestCode == GEIVIA_SHIZUKU_REQUEST) {
                shizukuGranted = result == PackageManager.PERMISSION_GRANTED
                shizukuStatus = if (shizukuGranted) "Shizuku conectado e autorizado para o GEIVIA." else "Autorização do Shizuku não concedida."
            }
        }
        Shizuku.addRequestPermissionResultListener(listener)
        onDispose { Shizuku.removeRequestPermissionResultListener(listener) }
    }
    LaunchedEffect(Unit) { refreshShizuku() }
    fun open(intent: Intent) {
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { runCatching { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) } }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("ANTES DE COMEÇAR • ACESSOS DO ANDROID", color = Color(0xFF07528D), fontSize = 11.sp, fontWeight = FontWeight.Black)
        Text("Prepare o aparelho para o GEIVIA", color = Color(0xFF102B43), fontSize = 25.sp, lineHeight = 31.sp, fontWeight = FontWeight.Bold)
        Text("Confira os acessos que podem evitar interrupções. O Android exige confirmação em algumas telas. Você pode continuar sem conceder opções que não deseja.", color = Color(0xFF587083), fontSize = 14.sp, lineHeight = 21.sp)
        AccessCard("Arquivos e pastas", "O GEIVIA usa sua pasta privada. Não precisa de acesso total ao armazenamento; importações e exportações usam o seletor oficial do Android.") {
            Text("✓ Nenhuma permissão ampla de arquivos será solicitada.", color = Color(0xFF18794E), fontSize = 12.sp)
        }
        AccessCard("Notificações", "Permite avisos de tarefas e processos em andamento. É opcional.") {
            if (Build.VERSION.SDK_INT >= 33 && !notifications) Button(onClick = { notificationRequest.launch(Manifest.permission.POST_NOTIFICATIONS) }, modifier = Modifier.fillMaxWidth()) { Text("Permitir notificações") }
            else Text(if (notifications) "✓ Autorizado ou não exigido nesta versão." else "Não autorizado.", color = Color(0xFF587083), fontSize = 12.sp)
        }
        AccessCard("Bateria e execução em segundo plano", "A otimização pode interromper instalações longas. A exceção é opcional e depende da confirmação do Android.") {
            if (Build.VERSION.SDK_INT >= 23 && !battery) {
                OutlinedButton(onClick = { open(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))) }, modifier = Modifier.fillMaxWidth()) { Text("Solicitar exceção de bateria") }
                OutlinedButton(onClick = { open(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }, modifier = Modifier.fillMaxWidth()) { Text("Ver configurações de bateria") }
            } else Text("✓ Exceção concedida ou não necessária.", color = Color(0xFF18794E), fontSize = 12.sp)
        }
        AccessCard("Início automático e configurações restritas", "As opções variam por fabricante. Não existe um comando universal para ativar autostart. Em Xiaomi/Redmi/POCO, procure Inicialização automática. Configurações restritas precisam ser liberadas manualmente nas Informações do app.") {
            OutlinedButton(onClick = { open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) }, modifier = Modifier.fillMaxWidth()) { Text("Abrir informações do GEIVIA") }
            Text("Só libere configurações restritas se confiar no app e entender a finalidade. O GEIVIA não tentará contornar o bloqueio.", color = Color(0xFF8A5715), fontSize = 11.sp, lineHeight = 16.sp)
        }
        AccessCard("Acesso especial ao uso do sistema", "O GEIVIA não precisa monitorar o uso de outros aplicativos nem obter acesso irrestrito ao sistema nesta etapa.") {
            Text("✓ Nenhum acesso de monitoramento será ativado.", color = Color(0xFF18794E), fontSize = 12.sp)
        }
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF4FA)), shape = RoundedCornerShape(15.dp)) {
            Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("MODO AVANÇADO • SHIZUKU SEM ROOT", color = Color(0xFF07528D), fontSize = 11.sp, fontWeight = FontWeight.Black)
                Text("Shizuku permite operações compatíveis com a identidade ADB (UID 2000), sem root. Não é acesso ilimitado: o Android ainda bloqueia comandos fora dos privilégios do shell.", color = Color(0xFF102B43), fontSize = 12.sp, lineHeight = 18.sp)
                Text(shizukuStatus, color = if (shizukuGranted) Color(0xFF18794E) else Color(0xFF587083), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                OutlinedButton(onClick = { open(Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/download/"))) }, modifier = Modifier.fillMaxWidth()) { Text("Instalar Shizuku") }
                OutlinedButton(onClick = { open(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)) }, modifier = Modifier.fillMaxWidth()) { Text("Abrir opções do desenvolvedor") }
                Button(onClick = {
                    refreshShizuku()
                    if (shizukuRunning && !shizukuGranted) runCatching { Shizuku.requestPermission(GEIVIA_SHIZUKU_REQUEST) }.onFailure { shizukuStatus = "Falha ao solicitar: ${it.message}" }
                }, modifier = Modifier.fillMaxWidth()) { Text(if (shizukuGranted) "Shizuku autorizado" else "Verificar / autorizar Shizuku") }
                Text("Para usar rish no Termux: inicie Shizuku, ative Depuração sem fio se solicitado e siga no app Shizuku a opção “Usar Shizuku em aplicativos de terminal”. Depois teste: rish -c 'id'. O resultado esperado é uid=2000(shell).", color = Color(0xFF102B43), fontSize = 11.sp, lineHeight = 16.sp)
                Text("Sem root, Shizuku precisa ser reiniciado após cada reinicialização do aparelho. O GEIVIA não executará comandos privilegiados sem uma ação clara do usuário.", color = Color(0xFF8A5715), fontSize = 10.sp, lineHeight = 14.sp)
            }
        }
        Button(onClick = {
            notifications = Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            battery = Build.VERSION.SDK_INT < 23 || power.isIgnoringBatteryOptimizations(context.packageName)
            onCompleted()
        }, modifier = Modifier.fillMaxWidth()) { Text("Continuar para preparar o GEIVIA") }
        Text("Essas opções poderão ser alteradas depois nas configurações do Android.", color = Color(0xFF587083), fontSize = 11.sp)
    }
}

@Composable
private fun AccessCard(title: String, description: String, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(15.dp)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(title, color = Color(0xFF102B43), fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(description, color = Color(0xFF587083), fontSize = 12.sp, lineHeight = 17.sp)
            content()
        }
    }
}
