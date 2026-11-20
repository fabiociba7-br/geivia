package com.geivia.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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

/**
 * Reconstructed preview from source-parser output. This is deliberately labeled as non-native:
 * the source scanner cannot evaluate arbitrary Compose/Kotlin code or runtime state.
 */
@Composable
internal fun ImportedScreenPreview(screen: ImportedScreen) {
    val parsedElements = screen.components.mapIndexed { index, component ->
        val sourceType = component.type.lowercase().replace("_", "")
        val normalizedType = when (sourceType) {
            "materialbutton", "appcompatbutton" -> "button"
            "outlinedbutton" -> "outlined_button"
            "textfield", "edittext", "outlinedtextfield" -> "textfield"
            "imageview" -> "image"
            "materialcardview", "surface" -> "card"
            "box", "lazycolumn", "lazyrow", "scaffold", "topappbar", "navigationbar",
            "constraintlayout", "linearlayout", "framelayout", "relativelayout" -> "section"
            "horizontalline", "horizontalseparator" -> "divider"
            "radiobutton" -> "checkbox"
            else -> sourceType.takeIf {
                it in setOf("text", "button", "outlined_button", "textfield", "image", "card", "section", "column", "row", "divider", "spacer", "switch", "checkbox")
            } ?: "text"
        }
        StudioElement(
            id = "imported_${screen.name.hashCode()}_$index",
            type = normalizedType,
            text = component.text ?: if (normalizedType == "text") "[${component.type}]" else "",
            padding = 8f,
            fillMaxWidth = true,
            enabled = false
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFE8EDF3))
    ) {
        Column(Modifier.fillMaxWidth().padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.width(92.dp).height(5.dp).background(Color(0xFF9AA8B7), RoundedCornerShape(8.dp)))
            Spacer(Modifier.height(9.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(15.dp),
                color = Color.White,
                shadowElevation = 2.dp
            ) {
                Column(
                    Modifier.fillMaxWidth().background(Color(0xFFF5F7FA)).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("9:41", color = Color(0xFF425466), fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.weight(1f))
                        Text("●  ●  ▰", color = Color(0xFF425466), fontSize = 8.sp)
                    }
                    Text(screen.name, color = Color(0xFF172B4D), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    if (parsedElements.isEmpty()) {
                        Text(
                            "Nenhum elemento visual foi interpretado neste arquivo. Abra o editor visual ou consulte os arquivos-fonte.",
                            color = Color(0xFF718096), fontSize = 11.sp
                        )
                    } else {
                        StudioScreenRuntime(
                            elements = parsedElements,
                            theme = StudioRuntimeTheme(),
                            runtimeValues = emptyMap(),
                            interactive = false,
                            onAction = { _, _, _ -> },
                            selectedId = null,
                            onSelect = {}
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Box(Modifier.width(42.dp).height(4.dp).background(Color(0xFF9AA8B7), RoundedCornerShape(8.dp)))
            Text(
                "Prévia reconstruída do código · não é execução nativa",
                Modifier.padding(top = 6.dp),
                color = Color(0xFF65758B),
                fontSize = 9.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}
