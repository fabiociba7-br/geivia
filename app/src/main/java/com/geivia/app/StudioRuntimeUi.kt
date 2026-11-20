package com.geivia.app

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Theme values shared by the editor canvas and the interactive Studio preview. */
internal data class StudioRuntimeTheme(
    val primary: Color = Color(0xFF1769AA),
    val background: Color = Color(0xFFF3F6FA),
    val text: Color = Color(0xFF17354D)
)

/**
 * Shared renderer for StudioElement trees. The editor uses interactive=false; preview uses
 * interactive=true. Action names and payloads intentionally match executePreviewActions:
 * on_click, on_value_change, and screen-level on_screen_open.
 */
@Composable
internal fun StudioScreenRuntime(
    elements: List<StudioElement>,
    theme: StudioRuntimeTheme,
    runtimeValues: Map<String, String>,
    interactive: Boolean,
    onAction: (String, String, String) -> Unit,
    selectedId: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val roots = elements.filter { it.parentId == null && it.visible }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        roots.forEach { root ->
            StudioRuntimeNode(
                element = root,
                all = elements,
                theme = theme,
                values = runtimeValues,
                interactive = interactive,
                onAction = onAction,
                selectedId = selectedId,
                onSelect = onSelect
            )
        }
    }
}

@Composable
private fun StudioRuntimeNode(
    element: StudioElement,
    all: List<StudioElement>,
    theme: StudioRuntimeTheme,
    values: Map<String, String>,
    interactive: Boolean,
    onAction: (String, String, String) -> Unit,
    selectedId: String?,
    onSelect: (String) -> Unit
) {
    if (!element.visible) return
    val context = LocalContext.current
    val children = all.filter { it.parentId == element.id && it.visible }
    val selected = !interactive && element.id == selectedId
    val type = element.type.lowercase()
    val isButton = type == "button"
    val background = studioRuntimeColor(element.backgroundColor, if (isButton) theme.primary else Color.White)
        .let { if (isButton && element.backgroundColor == "#FFFFFF") theme.primary else it }
    val foreground = when {
        isButton && element.textColor == "#17354D" -> Color.White
        element.textColor == "#17354D" -> theme.text
        else -> studioRuntimeColor(element.textColor, theme.text)
    }
    val shape = RoundedCornerShape(element.cornerRadius.coerceIn(0f, 48f).dp)
    val resolvedText = remember(element.text, values) {
        Regex("""\$\{([A-Za-z_][A-Za-z0-9_]*)\}""").replace(element.text) { values[it.groupValues[1]].orEmpty() }
    }
    val activate: () -> Unit = {
        onSelect(element.id)
        if (interactive && element.enabled) onAction(element.id, "on_click", "")
    }
    val outerModifier = Modifier
        .fillMaxWidthIf(element.fillMaxWidth)
        .then(if (element.heightDp != null) Modifier.height(element.heightDp.coerceAtLeast(0f).dp) else Modifier)
        .then(
            if (interactive && type in setOf("section", "column", "row")) Modifier.clickable(enabled = element.enabled) { activate() }
            else if (!interactive) Modifier.clickable { onSelect(element.id) }
            else Modifier
        )
        .then(if (selected) Modifier.border(2.dp, theme.primary, shape) else Modifier)
    val padding = element.padding.coerceIn(0f, 64f).dp

    when (type) {
        "section", "column", "row" -> {
            Surface(
                modifier = outerModifier,
                color = if (selected) Color(0xFFEAF3FB) else background,
                shape = shape,
            ) {
                Column(Modifier.then(if (element.fillMaxWidth) Modifier.fillMaxWidth() else Modifier.wrapContentWidth()).padding(padding), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (resolvedText.isNotBlank()) {
                        Text(resolvedText, color = foreground, fontSize = element.fontSize.sp, fontWeight = FontWeight.SemiBold)
                    }
                    if (type == "row") {
                        Row(Modifier.then(if (element.fillMaxWidth) Modifier.fillMaxWidth() else Modifier.wrapContentWidth()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            children.forEach { child ->
                                Box(Modifier.weight((child.weight ?: 1f).coerceAtLeast(0.01f))) {
                                    StudioRuntimeNode(child, all, theme, values, interactive, onAction, selectedId, onSelect)
                                }
                            }
                        }
                    } else {
                        children.forEach { child ->
                            if (child.weight != null) {
                                Box(Modifier.weight(child.weight.coerceIn(0.01f, 100f))) {
                                    StudioRuntimeNode(child, all, theme, values, interactive, onAction, selectedId, onSelect)
                                }
                            } else {
                                StudioRuntimeNode(child, all, theme, values, interactive, onAction, selectedId, onSelect)
                            }
                        }
                    }
                }
            }
        }
        "card" -> Card(
            modifier = outerModifier.clickable(enabled = interactive && element.enabled, onClick = activate),
            shape = shape,
            colors = CardDefaults.cardColors(containerColor = if (selected) Color(0xFFEAF3FB) else background)
        ) {
            Column(Modifier.then(if (element.fillMaxWidth) Modifier.fillMaxWidth() else Modifier.wrapContentWidth()).padding(padding), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (children.isEmpty()) Text(resolvedText, color = foreground, fontSize = element.fontSize.sp)
                else children.forEach { StudioRuntimeNode(it, all, theme, values, interactive, onAction, selectedId, onSelect) }
            }
        }
        "button", "outlined_button", "outlinedbutton" -> Box(outerModifier.padding(padding), contentAlignment = studioRuntimeAlignment(element.alignment)) {
            if (type == "button") Button(
                onClick = activate,
                enabled = element.enabled,
                shape = shape,
                colors = ButtonDefaults.buttonColors(containerColor = background),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp)
            ) { Text(resolvedText, color = foreground, fontSize = element.fontSize.sp) }
            else OutlinedButton(onClick = activate, enabled = element.enabled, shape = shape) {
                Text(resolvedText, color = foreground, fontSize = element.fontSize.sp)
            }
        }
        "textfield", "input", "field", "edittext", "outlinedtextfield" -> {
            var input by remember(element.id) { mutableStateOf(values[element.id].orEmpty()) }
            var wasFocused by remember(element.id) { mutableStateOf(false) }
            OutlinedTextField(
                value = input,
                onValueChange = { next ->
                    input = next
                    if (interactive && element.enabled) onAction(element.id, "on_value_change", next)
                },
                enabled = interactive && element.enabled,
                readOnly = !interactive,
                singleLine = true,
                label = { Text(resolvedText.ifBlank { element.placeholder ?: "Campo de entrada" }) },
                placeholder = element.placeholder?.let { { Text(it) } },
                modifier = outerModifier.padding(padding).onFocusChanged { focus ->
                    if (focus.isFocused && !wasFocused && interactive && element.enabled) {
                        onAction(element.id, "on_click", "")
                    }
                    wasFocused = focus.isFocused
                }
            )
        }
        "image", "imageview" -> {
            val bitmap by produceState<android.graphics.Bitmap?>(null, element.text) {
                value = if (element.text.startsWith("content://") || element.text.startsWith("file://")) {
                    withContext(Dispatchers.IO) {
                        runCatching {
                            context.contentResolver.openInputStream(android.net.Uri.parse(element.text))?.use { BitmapFactory.decodeStream(it) }
                        }.getOrNull()
                    }
                } else null
            }
            if (bitmap != null) {
                Image(
                    bitmap!!.asImageBitmap(),
                    contentDescription = element.text,
                    contentScale = if (element.contentScale.equals("crop", true)) ContentScale.Crop else ContentScale.Fit,
                    modifier = outerModifier.clickable(enabled = interactive && element.enabled, onClick = activate).padding(padding).clip(shape)
                )
            } else {
                Surface(outerModifier.padding(padding), shape, color = Color(0xFFF0F4F8)) {
                    Box(Modifier.then(if (element.fillMaxWidth) Modifier.fillMaxWidth() else Modifier.wrapContentWidth()).height(element.heightDp?.dp ?: 92.dp), contentAlignment = Alignment.Center) {
                        Text(if (element.text.isBlank()) "Imagem não disponível" else element.text, color = theme.text.copy(alpha = .65f), fontSize = 11.sp)
                    }
                }
            }
        }
        "divider", "horizontalline", "horizontalseparator" -> HorizontalDivider(Modifier.padding(vertical = padding))
        "spacer" -> Spacer(Modifier.fillMaxWidth().height((element.heightDp ?: element.padding.coerceAtLeast(8f)).dp))
        "checkbox", "switch", "radiobutton" -> {
            var checked by remember(element.id) { mutableStateOf(values[element.id] == "true") }
            Row(outerModifier.padding(padding), verticalAlignment = Alignment.CenterVertically) {
                Text(resolvedText.ifBlank { type }, Modifier.weight(1f), color = foreground, fontSize = element.fontSize.sp)
                if (type == "switch") Switch(checked, enabled = interactive && element.enabled, onCheckedChange = {
                    checked = it
                    if (interactive) onAction(element.id, "on_value_change", it.toString())
                }) else Checkbox(checked, enabled = interactive && element.enabled, onCheckedChange = {
                    checked = it
                    if (interactive) onAction(element.id, "on_value_change", it.toString())
                })
            }
        }
        else -> Surface(
            modifier = outerModifier.clickable(enabled = interactive && element.enabled, onClick = activate),
            color = background,
            shape = shape
        ) {
            Text(
                resolvedText,
                modifier = Modifier.then(if (element.fillMaxWidth) Modifier.fillMaxWidth() else Modifier.wrapContentWidth()).padding(padding + 4.dp),
                color = foreground,
                fontSize = element.fontSize.sp,
                textAlign = when (element.alignment) { "center" -> TextAlign.Center; "end" -> TextAlign.End; else -> TextAlign.Start }
            )
        }
    }
}

private fun Modifier.fillMaxWidthIf(enabled: Boolean): Modifier =
    if (enabled) fillMaxWidth() else this

private fun studioRuntimeAlignment(value: String): Alignment =
    when (value) { "center" -> Alignment.Center; "end" -> Alignment.CenterEnd; else -> Alignment.CenterStart }

private fun studioRuntimeColor(value: String, fallback: Color): Color =
    runCatching { Color(android.graphics.Color.parseColor(value)) }.getOrDefault(fallback)
