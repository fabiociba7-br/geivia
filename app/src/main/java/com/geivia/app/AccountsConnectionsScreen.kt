package com.geivia.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun AccountsConnectionsScreen(
    googleReady: Boolean,
    githubConnected: Boolean,
    onGoogle: () -> Unit,
    onGitHub: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("GEIVIA • CONTAS", color = Color(0xFF07528D), fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
        Text("Conectar minhas contas", color = Color(0xFF102B43), fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text("Conecte uma vez. O GEIVIA verifica o acesso e prepara as integrações automaticamente.", color = Color(0xFF587083), fontSize = 14.sp, lineHeight = 21.sp)
        Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(14.dp)) {
            Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Google Cloud + Firebase", color = Color(0xFF102B43), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text("Uma autorização Google para acessar os projetos Cloud e Firebase.", color = Color(0xFF587083), fontSize = 12.sp, lineHeight = 17.sp)
                    }
                    Text(if (googleReady) "Ferramentas prontas" else "Configurar", color = if (googleConnected) Color(0xFF236342) else Color(0xFF9A641B), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Button(onClick = onGoogle, modifier = Modifier.fillMaxWidth()) {
                    Text(if (googleReady) "Conectar / gerenciar conta Google" else "Preparar e conectar Google")
                }
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(14.dp)) {
            Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("GitHub", color = Color(0xFF102B43), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text("Autorize o acesso aos repositórios que deseja acompanhar.", color = Color(0xFF587083), fontSize = 12.sp, lineHeight = 17.sp)
                    }
                    Text(if (githubConnected) "Conectado" else "Pendente", color = if (githubConnected) Color(0xFF236342) else Color(0xFF9A641B), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Button(onClick = onGitHub, modifier = Modifier.fillMaxWidth()) {
                    Text(if (githubConnected) "Abrir GitHub" else "Conectar GitHub")
                }
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF4FA)), shape = RoundedCornerShape(14.dp)) {
            Text(
                "Não digite senhas aqui. O login acontece nas páginas oficiais dos provedores. O GEIVIA guarda somente as autorizações necessárias, protegidas no aparelho. Novos serviços aparecerão quando o projeto precisar deles.",
                Modifier.padding(14.dp), color = Color(0xFF31536B), fontSize = 12.sp, lineHeight = 18.sp
            )
        }
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Voltar ao painel") }
    }
}
