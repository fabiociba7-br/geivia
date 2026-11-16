package com.geivia.app

import java.io.File
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Restricted, app-private command interpreter.
 * It deliberately does not invoke /system/bin/sh or execute arbitrary binaries.
 */
class LocalTerminalEngine(filesDir: File) {
    private val workspace = File(filesDir, "terminal-workspace").canonicalFile

    init {
        if (!workspace.exists() && !workspace.mkdirs()) {
            throw IllegalStateException("Não foi possível criar o espaço de trabalho do terminal.")
        }
    }

    fun execute(rawCommand: String): String {
        if (rawCommand.length > MAX_COMMAND_LENGTH) return "Erro: comando excede o limite de $MAX_COMMAND_LENGTH caracteres."
        val trimmed = rawCommand.trim()
        if (trimmed.isEmpty()) return ""
        val parts = trimmed.split(Regex("\\s+"))
        val command = parts.first().lowercase(Locale.ROOT)
        val args = parts.drop(1)
        return try {
            when (command) {
                "help" -> HELP
                "pwd" -> workspace.absolutePath
                "whoami" -> "geivia (ambiente local isolado)"
                "date" -> SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.getDefault()).format(Date())
                "version" -> "GEIVIA Terminal 1.0 • interpretador local restrito"
                "echo" -> args.joinToString(" ")
                "ls" -> {
                    require(args.size <= 1) { "Uso: ls [diretório]" }
                    val target = resolve(args.firstOrNull() ?: ".")
                    require(target.isDirectory) { "Não é um diretório." }
                    target.listFiles()?.sortedBy { it.name.lowercase(Locale.ROOT) }
                        ?.joinToString("\n") { if (it.isDirectory) "${it.name}/" else it.name }
                        .orEmpty().ifBlank { "(diretório vazio)" }
                }
                "mkdir" -> {
                    require(args.size == 1) { "Uso: mkdir <nome>" }
                    val target = resolve(args[0])
                    require(!target.exists()) { "O caminho já existe." }
                    require(target.mkdirs()) { "Não foi possível criar o diretório." }
                    "Diretório criado: ${target.name}"
                }
                "touch" -> {
                    require(args.size == 1) { "Uso: touch <arquivo>" }
                    val target = resolve(args[0])
                    require(!target.exists() || target.isFile) { "O caminho não é um arquivo." }
                    if (!target.exists()) require(target.createNewFile()) { "Não foi possível criar o arquivo." }
                    "Arquivo disponível: ${target.name}"
                }
                "cat" -> {
                    require(args.size == 1) { "Uso: cat <arquivo>" }
                    val target = resolve(args[0])
                    require(target.isFile) { "Arquivo não encontrado." }
                    require(target.length() <= MAX_READ_BYTES) { "Arquivo excede o limite de leitura de 64 KB." }
                    String(target.readBytes(), StandardCharsets.UTF_8)
                }
                "clear" -> "__CLEAR__"
                "su", "sudo", "mount", "umount", "systemctl", "setenforce" ->
                    AndroidAccessPolicy.guidance(AndroidAccessUseCase.ROOT_OR_PROTECTED_SYSTEM_ACCESS)
                else -> "Comando não disponível: $command. Digite help para ver os comandos."
            }
        } catch (error: Exception) {
            "Erro: ${error.message ?: "não foi possível executar o comando."}"
        }
    }

    private fun resolve(path: String): File {
        require(path.isNotBlank()) { "Informe um caminho." }
        val candidate = File(workspace, path).canonicalFile
        val root = workspace.path
        require(candidate.path == root || candidate.path.startsWith(root + File.separator)) {
            "Acesso fora do espaço de trabalho não permitido."
        }
        return candidate
    }

    companion object {
        const val MAX_COMMAND_LENGTH = 2048
        const val MAX_READ_BYTES = 64 * 1024
        const val HELP = """GEIVIA Terminal — comandos disponíveis:
help                 mostra esta ajuda
pwd                  mostra a pasta de trabalho
ls [diretório]       lista arquivos do espaço GEIVIA
mkdir <nome>         cria uma pasta
touch <arquivo>      cria um arquivo vazio
cat <arquivo>        lê um arquivo de texto (máx. 64 KB)
echo <texto>         imprime texto
date                 mostra data e hora do aparelho
whoami               identifica o ambiente local
version              mostra a versão do terminal
clear                limpa a tela

Este terminal inicial é restrito à pasta privada do GEIVIA.
Ele não executa comandos Linux arbitrários nem acessa outros apps."""
    }
}
