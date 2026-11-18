package com.geivia.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Read-only phone mockup generated from the imported screen's visible source elements. */
@Composable
internal fun ImportedScreenPreview(screen: ImportedScreen) {
    val background = Color(0xFFF5F7FA)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFE8EDF3))
    ) {
        Column(Modifier.fillMaxWidth().padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.width(92.dp).height(5.dp).background(Color(0xFF9AA8B7), RoundedCornerShape(8.dp))
            )
            Spacer(Modifier.height(9.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(15.dp),
                color = Color.White,
                shadowElevation = 2.dp
            ) {
                Column(Modifier.fillMaxWidth().background(background).padding(12.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("9:41", color = Color(0xFF425466), fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.weight(1f))
                        Text("●  ●  ▰", color = Color(0xFF425466), fontSize = 8.sp)
                    }
                    Text(screen.name, color = Color(0xFF172B4D), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    HorizontalDivider(color = Color(0xFFE0E6ED))
                    if (screen.components.isEmpty()) {
                        Text("Não foi possível interpretar os elementos visuais deste arquivo.", color = Color(0xFF718096), fontSize = 11.sp)
                    } else {
                        screen.components.take(24).forEach { component ->
                            when (component.type.lowercase()) {
                                "text", "textview" -> Text(
                                    component.text?.ifBlank { "Texto da tela" } ?: "Texto da tela",
                                    color = Color(0xFF26364A), fontSize = 13.sp
                                )
                                "button", "materialbutton", "appcompatbutton" -> Button(
                                    onClick = {}, modifier = Modifier.fillMaxWidth()
                                ) { Text(component.text?.ifBlank { "Botão" } ?: "Botão", fontSize = 12.sp) }
                                "outlinedbutton" -> OutlinedButton(onClick = {}, modifier = Modifier.fillMaxWidth()) {
                                    Text(component.text?.ifBlank { "Botão" } ?: "Botão", fontSize = 12.sp)
                                }
                                "textfield", "edittext", "outlinedtextfield" -> OutlinedTextField(
                                    value = "", onValueChange = {}, readOnly = true,
                                    label = { Text(component.text?.ifBlank { "Campo de entrada" } ?: "Campo de entrada", fontSize = 11.sp) },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                "image", "imageview" -> Box(
                                    Modifier.fillMaxWidth().height(74.dp).background(Color(0xFFDCE5EF), RoundedCornerShape(10.dp)),
                                    contentAlignment = Alignment.Center
                                ) { Text("Imagem", color = Color(0xFF64748B), fontSize = 11.sp) }
                                "card", "materialcardview" -> Surface(
                                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp),
                                    color = Color.White, shadowElevation = 1.dp
                                ) { Text(component.text?.ifBlank { "Cartão" } ?: "Cartão", Modifier.padding(12.dp), color = Color(0xFF26364A), fontSize = 12.sp) }
                                "switch", "checkbox", "radiobutton" -> Text("◯  ${component.type}", color = Color(0xFF425466), fontSize = 11.sp)
                                "spacer" -> Spacer(Modifier.height(10.dp))
                                "divider", "horizontalline", "horizontalseparator" -> HorizontalDivider(color = Color(0xFFD5DDE6))
                                "column", "row", "box", "lazycolumn", "lazyrow", "scaffold", "surface", "topappbar", "navigationbar", "constraintlayout", "linearlayout", "framelayout", "relativelayout" -> {
                                    if (component.text != null) Text(component.text, color = Color(0xFF26364A), fontSize = 11.sp)
                                }
                                else -> Text(component.text ?: component.type, color = Color(0xFF64748B), fontSize = 10.sp)
                            }
                        }
                        if (screen.components.size > 24) {
                            Text("+ ${screen.components.size - 24} elementos", color = Color(0xFF718096), fontSize = 10.sp)
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Box(Modifier.width(42.dp).height(4.dp).background(Color(0xFF9AA8B7), RoundedCornerShape(8.dp)))
            Text("Prévia reconstruída a partir do código · não é execução nativa", Modifier.padding(top = 6.dp), color = Color(0xFF65758B), fontSize = 9.sp, textAlign = TextAlign.Center)
        }
    }
}
