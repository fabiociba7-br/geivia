package com.geivia.app

/**
 * Central registry for Android access. Every use case declares the narrowest
 * supported mechanism; broad storage access and privileged system access are
 * deliberately not represented as grantable runtime permissions.
 */
enum class AndroidAccessMechanism {
    APP_PRIVATE_STORAGE,
    OPEN_DOCUMENT,
    CREATE_DOCUMENT,
    OPEN_DOCUMENT_TREE,
    NORMAL_RUNTIME_PERMISSION,
    UNSUPPORTED_PRIVILEGE
}

enum class AndroidAccessUseCase(
    val mechanism: AndroidAccessMechanism,
    val explanation: String,
    val mimeTypes: List<String> = emptyList()
) {
    APP_WORKSPACE(
        AndroidAccessMechanism.APP_PRIVATE_STORAGE,
        "O GEIVIA usa sua própria pasta privada. O Android não exige permissão de armazenamento para isso."
    ),
    EXPORT_HISTORY_BACKUP(
        AndroidAccessMechanism.CREATE_DOCUMENT,
        "O Android abrirá o seletor para você escolher onde salvar o backup. O GEIVIA grava somente o arquivo escolhido.",
        listOf("application/json")
    ),
    VALIDATE_HISTORY_BACKUP(
        AndroidAccessMechanism.OPEN_DOCUMENT,
        "O Android abrirá o seletor. O GEIVIA lê somente o backup que você selecionar.",
        listOf("application/json", "text/json")
    ),
    IMPORT_CLOUD_PROJECTS(
        AndroidAccessMechanism.OPEN_DOCUMENT,
        "O Android abrirá o seletor. O GEIVIA lê somente o inventário JSON selecionado.",
        listOf("application/json", "text/*", "*/*")
    ),
    SELECT_PROJECT_FOLDER(
        AndroidAccessMechanism.OPEN_DOCUMENT_TREE,
        "O Android permitirá escolher uma pasta específica. O GEIVIA não terá acesso geral ao armazenamento."
    ),
    REQUEST_DEVICE_RUNTIME_PERMISSION(
        AndroidAccessMechanism.NORMAL_RUNTIME_PERMISSION,
        "A permissão do Android será solicitada somente quando a função que precisa dela for iniciada."
    ),
    ROOT_OR_PROTECTED_SYSTEM_ACCESS(
        AndroidAccessMechanism.UNSUPPORTED_PRIVILEGE,
        "O Android não oferece uma permissão comum para acesso root ou áreas protegidas do sistema. Esta operação exige privilégios que um aplicativo comum não pode conceder a si mesmo."
    )
}

object AndroidAccessPolicy {
    fun guidance(useCase: AndroidAccessUseCase): String = useCase.explanation

    fun requiresRuntimePermission(useCase: AndroidAccessUseCase): Boolean =
        useCase.mechanism == AndroidAccessMechanism.NORMAL_RUNTIME_PERMISSION

    fun isSupportedWithoutRoot(useCase: AndroidAccessUseCase): Boolean =
        useCase.mechanism != AndroidAccessMechanism.UNSUPPORTED_PRIVILEGE

    fun requiredManifestPermissionsForCurrentFeatures(): Set<String> = setOf(
        "android.permission.INTERNET",
        "android.permission.POST_NOTIFICATIONS",
        "android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS"
    )
}
