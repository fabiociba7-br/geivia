package com.geivia.app

import android.os.Build

internal object DeviceCompatibility {
    fun isXiaomiFamily(manufacturer: String, brand: String): Boolean {
        val values = listOf(manufacturer, brand).map { it.trim().lowercase() }
        return values.any { value ->
            value == "xiaomi" || value == "redmi" || value == "poco" ||
                value.startsWith("xiaomi ") || value.startsWith("redmi ") || value.startsWith("poco ")
        }
    }

    fun isCurrentDeviceXiaomiFamily(): Boolean =
        isXiaomiFamily(Build.MANUFACTURER.orEmpty(), Build.BRAND.orEmpty())

    fun deviceLabel(): String = listOf(Build.MANUFACTURER, Build.BRAND, Build.MODEL)
        .filterNotNull()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
        .joinToString(" ")
        .ifBlank { "Dispositivo Android" }

    fun hyperOsGuidance(): List<String> = listOf(
        "Arquivos: use Importar arquivos ou Importar pasta dentro do GEIVIA. O seletor oficial do Android concede acesso somente ao que você escolher.",
        "Autoinicialização: se uma futura função de execução em segundo plano precisar disso, habilite manualmente em Configurações > Apps > Permissões > Inicialização automática em segundo plano (o nome pode variar).",
        "Bateria: se uma tarefa real do GEIVIA precisar continuar após sair do app, abra Informações do app > Economia de bateria e avalie Sem restrições. Não é necessário liberar isso para importar arquivos ou usar o painel atual.",
        "Permissões: confira Informações do app > Permissões. Conceda somente o acesso solicitado por uma função que você decidiu usar.",
        "Sistema/root: HyperOS não permite que um app comum force acesso a /system, dados de outros apps ou privilégios root. O GEIVIA deve informar a limitação, não simular autorização."
    )
}
