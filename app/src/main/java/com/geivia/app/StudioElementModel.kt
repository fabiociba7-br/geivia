package com.geivia.app

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Persisted Studio component schema. New properties always have backwards-safe defaults. */
internal data class StudioElement(
    val id: String = UUID.randomUUID().toString(),
    val type: String,
    val text: String,
    val fontSize: Float = 16f,
    val padding: Float = 12f,
    val alignment: String = "start",
    val parentId: String? = null,
    val backgroundColor: String = "#FFFFFF",
    val textColor: String = "#17354D",
    val cornerRadius: Float = 10f,
    val visible: Boolean = true,
    val weight: Float? = null,
    val fillMaxWidth: Boolean = true,
    val heightDp: Float? = null,
    val enabled: Boolean = true,
    val placeholder: String? = null,
    val contentScale: String = "fit"
)

internal object StudioElementJsonCodec {
    fun decodeList(source: String?): List<StudioElement> {
        if (source.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(source)
            (0 until array.length()).map { index ->
                val item = array.getJSONObject(index)
                StudioElement(
                    id = item.optString("id", UUID.randomUUID().toString()),
                    type = item.optString("type", "text"),
                    text = item.optString("text", ""),
                    fontSize = item.optDouble("fontSize", 16.0).toFloat().coerceIn(8f, 48f),
                    padding = item.optDouble("padding", 12.0).toFloat().coerceIn(0f, 64f),
                    alignment = item.optString("alignment", "start").let { if (it in setOf("start", "center", "end")) it else "start" },
                    parentId = item.optString("parentId").takeIf { it.isNotBlank() },
                    backgroundColor = item.optString("backgroundColor", "#FFFFFF"),
                    textColor = item.optString("textColor", "#17354D"),
                    cornerRadius = item.optDouble("cornerRadius", 10.0).toFloat().coerceIn(0f, 48f),
                    visible = item.optBoolean("visible", true),
                    weight = if (item.has("weight") && !item.isNull("weight")) item.optDouble("weight").toFloat().coerceIn(0.01f, 100f) else null,
                    fillMaxWidth = item.optBoolean("fillMaxWidth", true),
                    heightDp = if (item.has("heightDp") && !item.isNull("heightDp")) item.optDouble("heightDp").toFloat().coerceIn(0f, 2000f) else null,
                    enabled = item.optBoolean("enabled", true),
                    placeholder = item.optString("placeholder").takeIf { it.isNotBlank() },
                    contentScale = item.optString("contentScale", "fit").let { if (it in setOf("fit", "crop")) it else "fit" }
                )
            }
        }.getOrDefault(emptyList())
    }

    fun encodeList(elements: List<StudioElement>): String {
        val array = JSONArray()
        elements.forEach { element ->
            array.put(JSONObject()
                .put("id", element.id)
                .put("type", element.type)
                .put("text", element.text)
                .put("fontSize", element.fontSize.toDouble())
                .put("padding", element.padding.toDouble())
                .put("alignment", element.alignment)
                .put("parentId", element.parentId ?: "")
                .put("backgroundColor", element.backgroundColor)
                .put("textColor", element.textColor)
                .put("cornerRadius", element.cornerRadius.toDouble())
                .put("visible", element.visible)
                .put("weight", element.weight?.toDouble() ?: JSONObject.NULL)
                .put("fillMaxWidth", element.fillMaxWidth)
                .put("heightDp", element.heightDp?.toDouble() ?: JSONObject.NULL)
                .put("enabled", element.enabled)
                .put("placeholder", element.placeholder ?: "")
                .put("contentScale", element.contentScale))
        }
        return array.toString()
    }
}
