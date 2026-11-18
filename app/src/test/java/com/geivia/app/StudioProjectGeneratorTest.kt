package com.geivia.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StudioProjectGeneratorTest {
    @Test
    fun createsAndroidComposeProjectFilesFromScreenSchema() {
        val screens = listOf(
            StudioScreenDefinition("home", "Início"),
            StudioScreenDefinition("profile", "Perfil")
        )
        val components = mapOf(
            "home" to listOf(
                StudioGeneratedElement("title", "text", "Olá", 22f, 12f, "start", null, "#FFFFFF", "#17354D", 10f, true),
                StudioGeneratedElement("continue", "button", "Continuar", 16f, 12f, "center", null, "#FFFFFF", "#17354D", 10f, true)
            ),
            "profile" to emptyList()
        )

        val files = StudioProjectGenerator.files(
            packageName = "com.example.generated",
            appName = "Meu aplicativo",
            screens = screens,
            components = components,
            startScreenId = "home",
            primaryColor = "#1769AA",
            backgroundColor = "#F3F6FA",
            textColor = "#17354D"
        )

        assertTrue(files.containsKey("settings.gradle.kts"))
        assertTrue(files.containsKey("gradle.properties"))
        assertTrue(files.containsKey("app/build.gradle.kts"))
        assertTrue(files.containsKey("app/src/main/AndroidManifest.xml"))
        val source = files["app/src/main/java/com/example/generated/MainActivity.kt"].orEmpty()
        assertTrue(source.contains("class MainActivity"))
        assertTrue(source.contains("Screen_Home"))
        assertTrue(source.contains("Screen_Profile"))
        assertTrue(source.contains("Text(resolve(\"Olá\""))
        assertTrue(source.contains("Button(onClick = { runActions("))
        assertEquals(true, files["gradle.properties"]?.contains("android.useAndroidX=true") == true)
    }

    @Test
    fun generatesDeclaredActionsAndRuntimeBindings() {
        val logic = StudioLogicDocument(
            variables = listOf(StudioVariableDefinition("userName", "Nome", "text", "Ana")),
            actions = listOf(
                StudioActionDefinition(
                    id = "show_welcome",
                    trigger = "on_click",
                    kind = "show_message",
                    componentId = "continue",
                    expression = "Bem-vinda"
                )
            )
        )
        val files = StudioProjectGenerator.files(
            packageName = "com.example.actions",
            appName = "App com ações",
            screens = listOf(StudioScreenDefinition("home", "Início")),
            components = mapOf(
                "home" to listOf(
                    StudioGeneratedElement("continue", "button", "Continuar", 16f, 12f, "center", null, "#FFFFFF", "#17354D", 10f, true)
                )
            ),
            startScreenId = "home",
            primaryColor = "#1769AA",
            backgroundColor = "#F3F6FA",
            textColor = "#17354D",
            logic = logic
        )
        val source = files.values.first { it.contains("class MainActivity") }
        assertTrue(source.contains("fun runAction"))
        assertTrue(source.contains("fun runActions"))
        assertTrue(source.contains("runAction(\"show_welcome\""))
        assertTrue(source.contains("runActions(\"continue\", \"on_click\""))
        assertTrue(source.contains("if (trigger != \"on_screen_open\") navigationCount = 0"))
    }

    @Test
    fun enforcesRequiredScreenInputsBeforeGeneratedNavigation() {
        val files = StudioProjectGenerator.files(
            packageName = "com.example.required",
            appName = "App com entradas",
            screens = listOf(
                StudioScreenDefinition("home", "Início"),
                StudioScreenDefinition(
                    "profile", "Perfil",
                    listOf(StudioScreenInputParameter("email", "E-mail", "text", required = true))
                )
            ),
            components = mapOf("home" to listOf(
                StudioGeneratedElement("openProfile", "button", "Abrir perfil", 16f, 12f, "center", null, "#FFFFFF", "#17354D", 10f, true)
            )),
            startScreenId = "home",
            primaryColor = "#1769AA",
            backgroundColor = "#F3F6FA",
            textColor = "#17354D",
            logic = StudioLogicDocument(actions = listOf(
                StudioActionDefinition("go", "on_click", "navigate", target = "profile", componentId = "openProfile")
            ))
        )
        val source = files.values.first { it.contains("class MainActivity") }
        assertTrue(source.contains("missingRequired"))
        assertTrue(source.contains("Informe os campos obrigatórios"))
        assertTrue(source.contains("""listOf<String>("email")"""))
    }

    @Test
    fun escapesTextAndUsesSafePackagePath() {
        val files = StudioProjectGenerator.files(
            packageName = "com.example.app",
            appName = "Meu app",
            screens = listOf(StudioScreenDefinition("home", "Início")),
            components = mapOf(
                "home" to listOf(
                    StudioGeneratedElement("label", "text", "Texto com \"aspas\"", 16f, 8f, "start", null, "#FFFFFF", "#17354D", 8f, true)
                )
            ),
            startScreenId = "home",
            primaryColor = "#1769AA",
            backgroundColor = "#FFFFFF",
            textColor = "#17354D"
        )
        val source = files.values.first { it.contains("class MainActivity") }
        assertTrue(source.contains("Texto com \\\"aspas\\\""))
        assertTrue(files.keys.any { it == "app/src/main/java/com/example/app/MainActivity.kt" })
    }
}
