package com.geivia.app

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private val StudioBlue = Color(0xFF1769AA)
private val StudioNavy = Color(0xFF12324A)
private val StudioOrange = Color(0xFFF29A21)
private val StudioBackground = Color(0xFFF4F7FB)
private val StudioMuted = Color(0xFF6B7C8F)

private data class StudioNavItem(
    val section: String,
    val title: String,
    val icon: ImageVector,
    val action: () -> Unit = {},
    val enabled: Boolean = true
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StudioDashboardScreen(
    context: Context,
    onNewProject: () -> Unit,
    onProjects: () -> Unit,
    onEditor: () -> Unit,
    onPreview: () -> Unit,
    onFirebase: () -> Unit,
    onIntegrations: () -> Unit,
    onTools: () -> Unit,
    onDiagnostics: () -> Unit,
    onAccounts: () -> Unit,
    onLegacyDashboard: () -> Unit,
    onTerminal: () -> Unit = {},
    onCodeEditor: () -> Unit = {}
) {
    val prefs = context.getSharedPreferences("geivia_onboarding", Context.MODE_PRIVATE)
    val projectId = prefs.getString("selected_project_id", "").orEmpty()
    val projectName = prefs.getString("selected_project_name", "").orEmpty().ifBlank { projectId }
    val importedProjectPath = ProjectWorkspaceInfo.activeRoot(context)?.absolutePath.orEmpty()
    val importedProjectExists = importedProjectPath.isNotBlank() && java.io.File(importedProjectPath).isDirectory
    val importedProjectName = prefs.getString("imported_project_name", "").orEmpty()
        .ifBlank { java.io.File(importedProjectPath).name }
    val drawerState = androidx.compose.material3.rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val navigate: ( () -> Unit ) -> Unit = { action ->
        scope.launch { drawerState.close() }
        action()
    }
    val navItems = listOf(
        StudioNavItem("WORKSPACE", "Visão geral", Icons.Default.Dashboard, {}),
        StudioNavItem("WORKSPACE", "Meus projetos", Icons.Default.FolderOpen, { navigate(onProjects) }),
        StudioNavItem("CRIAR APLICATIVOS", "Editor visual", Icons.Default.Widgets, { navigate(onEditor) }),
        StudioNavItem("CRIAR APLICATIVOS", "Biblioteca de componentes", Icons.Default.Extension, { navigate(onEditor) }),
        StudioNavItem("CRIAR APLICATIVOS", "Ações e lógica", Icons.Default.AccountTree, { navigate(onEditor) }),
        StudioNavItem("DADOS E SERVIÇOS", "Firebase e dados", Icons.Default.CloudQueue, { navigate(onFirebase) }),
        StudioNavItem("DADOS E SERVIÇOS", "Integrações", Icons.Default.Extension, { navigate(onIntegrations) }),
        StudioNavItem("TESTAR E PUBLICAR", "Pré-visualização", Icons.Default.Visibility, { navigate(onPreview) }),
        StudioNavItem("TESTAR E PUBLICAR", "Operações do projeto", Icons.Default.Publish, { navigate(onProjects) }),
        StudioNavItem("DESENVOLVIMENTO", "Terminal", Icons.Default.Terminal, { navigate(onTerminal) }),
        StudioNavItem("DESENVOLVIMENTO", "Editor de código", Icons.Default.Edit, { navigate(onCodeEditor) }),
        StudioNavItem("AMBIENTE", "Ferramentas e atualizações", Icons.Default.Terminal, { navigate(onTools) }),
        StudioNavItem("AMBIENTE", "Diagnóstico", Icons.Default.Build, { navigate(onDiagnostics) }),
        StudioNavItem("AMBIENTE", "Acompanhamento clássico", Icons.Default.History, { navigate(onLegacyDashboard) }),
        StudioNavItem("CONTA", "Contas e acesso", Icons.Default.AccountCircle, { navigate(onAccounts) })
    )

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = Color.White,
                drawerContentColor = StudioNavy
            ) {
                Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 24.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(42.dp).background(StudioBlue, RoundedCornerShape(13.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.AccountTree, null, tint = Color.White, modifier = Modifier.size(25.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("GEIVIA", fontSize = 21.sp, fontWeight = FontWeight.ExtraBold, color = StudioNavy, letterSpacing = 1.sp)
                            Text("APP STUDIO", fontSize = 10.sp, color = StudioBlue, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                        }
                    }
                    Spacer(Modifier.height(28.dp))
                    navItems.forEachIndexed { index, item ->
                        if (index == 0 || navItems[index - 1].section != item.section) {
                            Spacer(Modifier.height(if (index == 0) 4.dp else 14.dp))
                            Text(item.section, color = StudioMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                            Spacer(Modifier.height(5.dp))
                        }
                        if (item.enabled) {
                            NavigationDrawerItem(
                                label = {
                                    Text(item.title, fontWeight = if (index == 0) FontWeight.SemiBold else FontWeight.Normal)
                                },
                                icon = { Icon(item.icon, null) },
                                selected = index == 0,
                                onClick = item.action,
                                modifier = Modifier.padding(vertical = 1.dp),
                                colors = NavigationDrawerItemDefaults.colors(
                                    selectedContainerColor = Color(0xFFE8F2FC),
                                    selectedIconColor = StudioBlue,
                                    selectedTextColor = StudioBlue,
                                    unselectedIconColor = StudioMuted,
                                    unselectedTextColor = StudioNavy
                                )
                            )
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(item.icon, null, tint = Color(0xFFB7C1CC), modifier = Modifier.size(24.dp))
                                Spacer(Modifier.width(12.dp))
                                Text(item.title, modifier = Modifier.weight(1f), color = Color(0xFF9AA7B4), fontSize = 14.sp)
                                Text("EM BREVE", color = StudioMuted, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(color = Color(0xFFE8EDF3))
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AccountCircle, null, tint = StudioBlue, modifier = Modifier.size(19.dp))
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("Conta e permissões", color = StudioNavy, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            Text("Google Cloud • Firebase • GitHub", color = StudioMuted, fontSize = 10.sp)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Plataforma Android", color = StudioNavy, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    Text("Plataformas do GEIVIA", color = StudioMuted, fontSize = 10.sp)
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    ) {
        Scaffold(
            containerColor = StudioBackground,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("Workspace", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = StudioNavy)
                            Text("Seu espaço de criação", fontSize = 11.sp, color = StudioMuted)
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Abrir menu", tint = StudioNavy)
                        }
                    },
                    actions = {
                        Surface(shape = CircleShape, color = Color(0xFFE8F2FC)) {
                            Text("STUDIO", modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp), color = StudioBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        }
                        Spacer(Modifier.width(14.dp))
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = StudioBackground)
                )
            }
        ) { padding ->
            Column(
                Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Text("Olá! Vamos construir?", color = StudioNavy, fontSize = 27.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(5.dp))
                Text("Crie, organize e prepare aplicativos Android nativos em um só lugar.", color = StudioMuted, fontSize = 14.sp, lineHeight = 20.sp)
                Spacer(Modifier.height(20.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = StudioNavy)
                ) {
                    Column(Modifier.fillMaxWidth().padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(color = Color(0x332F9BE8), shape = RoundedCornerShape(9.dp)) {
                                Text("GEIVIA APP STUDIO", color = Color(0xFFB9DFFF), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp))
                            }
                            Spacer(Modifier.weight(1f))
                            Icon(Icons.Default.AutoAwesome, null, tint = StudioOrange)
                        }
                        Spacer(Modifier.height(14.dp))
                        Text("Do primeiro componente ao app publicado.", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, lineHeight = 28.sp)
                        Spacer(Modifier.height(7.dp))
                        Text("Uma plataforma para planejar, montar e evoluir seus projetos.", color = Color(0xFFD2DFEA), fontSize = 13.sp, lineHeight = 19.sp)
                        Spacer(Modifier.height(18.dp))
                        Button(
                            onClick = onNewProject,
                            colors = ButtonDefaults.buttonColors(containerColor = StudioOrange, contentColor = StudioNavy),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Add, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Novo projeto", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(Modifier.height(18.dp))
                Text("SEU WORKSPACE", color = StudioMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
                Spacer(Modifier.height(9.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StudioMetricCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.FolderOpen,
                        value = if (importedProjectExists || projectId.isNotBlank()) "1" else "0",
                        label = "Projeto ativo"
                    )
                    StudioMetricCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Devices,
                        value = "1",
                        label = "Plataforma"
                    )
                }

                Spacer(Modifier.height(18.dp))
                Text("CONTINUE DE ONDE PAROU", color = StudioMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
                Spacer(Modifier.height(9.dp))
                Card(
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onProjects),
                    shape = RoundedCornerShape(17.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(44.dp).background(Color(0xFFEAF3FC), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.FolderOpen, null, tint = StudioBlue)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(if (importedProjectExists) importedProjectName else if (projectId.isBlank()) "Nenhum projeto selecionado" else projectName, color = StudioNavy, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(if (importedProjectExists) "Projeto local importado • toque para abrir e gerenciar os arquivos" else if (projectId.isBlank()) "Crie um projeto ou importe um existente para começar." else "Project ID: $projectId", color = StudioMuted, fontSize = 11.sp, maxLines = 2)
                        }
                        Icon(Icons.Default.ChevronRight, null, tint = StudioMuted)
                    }
                }

                Spacer(Modifier.height(18.dp))
                Text("FERRAMENTAS DO STUDIO", color = StudioMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
                Spacer(Modifier.height(9.dp))
                StudioActionCard(Icons.Default.Widgets, "Editor visual", "Monte telas com componentes e edite suas propriedades", true, onEditor)
                Spacer(Modifier.height(8.dp))
                StudioActionCard(Icons.Default.Preview, "Pré-visualizar projeto", "Confira os arquivos e a estrutura importada", true, onPreview)
                Spacer(Modifier.height(8.dp))
                StudioActionCard(Icons.Default.CloudQueue, "Dados e Firebase", "Conecte serviços e acompanhe a configuração", true, onFirebase)
                 Spacer(Modifier.height(8.dp))
                StudioActionCard(Icons.Default.Extension, "Integrações", "Contas Google, GitHub e conexões", true, onIntegrations)
                Spacer(Modifier.height(8.dp))
                StudioActionCard(Icons.Default.AccountCircle, "Contas e acesso", "Gerencie as autorizações do Google Cloud, Firebase e GitHub", true, onAccounts)

                Spacer(Modifier.height(18.dp))
                Card(shape = RoundedCornerShape(17.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF7EA))) {
                    Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Default.Lightbulb, null, tint = Color(0xFFB87516))
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("Evolução da plataforma", color = StudioNavy, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Spacer(Modifier.height(4.dp))
                            Text("O editor visual já permite adicionar, selecionar, editar, reorganizar e salvar componentes. Árvore hierárquica, arrastar e soltar, dados dinâmicos, ações e geração de aplicativos Android serão evoluções seguintes.", color = StudioMuted, fontSize = 12.sp, lineHeight = 18.sp)
                        }
                    }
                }
                Spacer(Modifier.height(26.dp))
            }
        }
    }
}

@Composable
private fun StudioMetricCard(modifier: Modifier, icon: ImageVector, value: String, label: String) {
    Card(modifier = modifier, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Icon(icon, null, tint = StudioBlue, modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(10.dp))
            Text(value, color = StudioNavy, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
            Text(label, color = StudioMuted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun StudioActionCard(icon: ImageVector, title: String, description: String, enabled: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        shape = RoundedCornerShape(15.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).background(if (enabled) Color(0xFFEAF3FC) else Color(0xFFF0F2F5), RoundedCornerShape(11.dp)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = if (enabled) StudioBlue else StudioMuted)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = StudioNavy, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text(description, color = StudioMuted, fontSize = 11.sp, lineHeight = 16.sp)
            }
            if (enabled) Icon(Icons.Default.ChevronRight, null, tint = StudioMuted)
        }
    }
}
