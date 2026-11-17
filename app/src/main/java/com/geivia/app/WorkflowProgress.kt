package com.geivia.app

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persistent, project-scoped workflow state.
 *
 * A copied command is never considered completed. External work is completed only
 * after a fresh diagnostic has supplied evidence for the same project and step.
 */
enum class WorkflowStepState {
    NOT_STARTED,
    IN_PROGRESS,
    WAITING_CONFIRMATION,
    UNKNOWN,
    COMPLETED,
    USER_CONFIRMED,
    FAILED
}

data class WorkflowStepRecord(
    val projectId: String,
    val stepId: String,
    val state: WorkflowStepState,
    val updatedAt: Long,
    val operationId: String,
    val message: String,
    val evidence: String,
    val preparedAt: Long = 0L
)

data class WorkflowBackupValidationResult(val valid: Boolean, val message: String)

data class WorkflowHistoryEntry(
    val projectId: String,
    val stepId: String,
    val state: String,
    val updatedAt: Long,
    val operationId: String,
    val message: String,
    val evidence: String
)

object WorkflowBackupValidator {
    private const val FORMAT = "GEIVIA_WORKFLOW_BACKUP"
    private const val SUPPORTED_VERSION = 1

    /** Validates the backup envelope and raw-content hashes only; never imports or mutates state. */
    fun validate(rawJson: String): WorkflowBackupValidationResult {
        return try {
            val backup = JSONObject(rawJson)
            if (backup.getString("format") != FORMAT) {
                return WorkflowBackupValidationResult(false, "Formato de backup não reconhecido.")
            }
            if (backup.getInt("version") != SUPPORTED_VERSION) {
                return WorkflowBackupValidationResult(false, "Versão de backup não suportada.")
            }
            val recordsRaw = backup.getString("recordsRaw")
            val historyRaw = backup.getString("historyRaw")
            val recordsHash = backup.getString("recordsSha256")
            val historyHash = backup.getString("historySha256")
            val hashPattern = Regex("^[a-fA-F0-9]{64}$")
            if (!hashPattern.matches(recordsHash) || !hashPattern.matches(historyHash)) {
                return WorkflowBackupValidationResult(false, "Hash ausente ou em formato inválido.")
            }
            fun digest(value: String): ByteArray =
                java.security.MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
            fun decodeHex(value: String): ByteArray = ByteArray(value.length / 2) { index ->
                value.substring(index * 2, index * 2 + 2).toInt(16).toByte()
            }
            val recordsValid = java.security.MessageDigest.isEqual(digest(recordsRaw), decodeHex(recordsHash))
            val historyValid = java.security.MessageDigest.isEqual(digest(historyRaw), decodeHex(historyHash))
            if (!recordsValid || !historyValid) {
                WorkflowBackupValidationResult(false, "O conteúdo não corresponde aos hashes SHA-256. O arquivo pode ter sido alterado ou danificado.")
            } else {
                WorkflowBackupValidationResult(true, "Integridade do arquivo e dos dois conteúdos confirmada.")
            }
        } catch (_: Exception) {
            WorkflowBackupValidationResult(false, "Arquivo inválido ou incompleto. Nenhum dado foi importado.")
        }
    }
}

class WorkflowProgressStore(private val prefs: SharedPreferences) {
    companion object {
        private const val KEY_RECORDS = "workflow_step_records_v1"
        private const val KEY_HISTORY = "workflow_step_history_v1"
    }

    /**
     * Exports the exact persisted strings, including malformed data, without parsing,
     * repairing, or changing them. The backup is a recovery aid, never an authorization
     * to repeat an operation.
     */
    @Synchronized
    fun exportBackupJson(): String {
        val recordsRaw = prefs.getString(KEY_RECORDS, "{}") ?: "{}"
        val historyRaw = prefs.getString(KEY_HISTORY, "[]") ?: "[]"
        fun sha256(value: String): String = java.security.MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return JSONObject().apply {
            put("format", "GEIVIA_WORKFLOW_BACKUP")
            put("version", 1)
            put("exportedAt", System.currentTimeMillis())
            put("recordsRaw", recordsRaw)
            put("historyRaw", historyRaw)
            put("recordsSha256", sha256(recordsRaw))
            put("historySha256", sha256(historyRaw))
            put("recoveryWarning", "Arquivo de backup não autoriza repetir operações. Reconcile cada operação pendente com diagnóstico atualizado do mesmo Project ID.")
        }.toString(2)
    }

    /** Returns a safe diagnostic without clearing or rewriting corrupted persisted data. */
    @Synchronized
    fun healthError(): String? = runCatching {
        readRecords()
        readHistory()
        null
    }.getOrElse { error ->
        error.message ?: "O histórico local está inválido. As operações foram bloqueadas."
    }

    @Synchronized
    fun get(projectId: String, stepId: String): WorkflowStepRecord {
        require(projectId.isNotBlank()) { "Project ID obrigatório." }
        require(stepId.isNotBlank()) { "Identificador da etapa obrigatório." }
        val record = readRecords().optJSONObject(recordKey(projectId, stepId))
        return record?.toRecord(projectId, stepId) ?: WorkflowStepRecord(
            projectId, stepId, WorkflowStepState.NOT_STARTED, 0L, "", "", "", 0L
        )
    }

    /** Persists a review gate. This does not mean the external operation ran. */
    @Synchronized
    fun waitForConfirmation(projectId: String, stepId: String, message: String): WorkflowStepRecord {
        val current = get(projectId, stepId)
        check(current.state != WorkflowStepState.UNKNOWN) {
            "A operação anterior tem resultado desconhecido. Reconcilie-a antes de continuar."
        }
        check(current.state != WorkflowStepState.IN_PROGRESS) {
            "Já existe uma operação em andamento. Faça o diagnóstico da operação atual antes de preparar outra."
        }
        check(current.state != WorkflowStepState.USER_CONFIRMED) {
            "A conclusão foi declarada manualmente, mas ainda não foi verificada. Faça o diagnóstico antes de preparar outra operação."
        }
        check(current.state != WorkflowStepState.COMPLETED) {
            "Esta etapa já foi confirmada como concluída."
        }
        val now = System.currentTimeMillis()
        return save(current.copy(
            state = WorkflowStepState.WAITING_CONFIRMATION,
            updatedAt = now,
            preparedAt = now,
            message = message,
            evidence = ""
        ))
    }

    /** Call only after the user explicitly confirms the exact operation. */
    @Synchronized
    fun confirmAndStart(projectId: String, stepId: String, operationId: String, message: String): WorkflowStepRecord {
        require(operationId.isNotBlank()) { "Identificador da operação obrigatório." }
        val current = get(projectId, stepId)
        check(current.state == WorkflowStepState.WAITING_CONFIRMATION) {
            "A etapa exige uma nova confirmação explícita antes de iniciar. Estado atual: ${current.state}."
        }
        check(operationId != current.operationId) {
            "Cada tentativa precisa de um identificador de operação novo."
        }
        val now = System.currentTimeMillis()
        return save(current.copy(
            state = WorkflowStepState.IN_PROGRESS,
            updatedAt = now,
            preparedAt = now,
            operationId = operationId,
            message = message,
            evidence = ""
        ))
    }

    /** Moves a prepared manual command to execution tracking only after the user attests it was run. */
    @Synchronized
    fun markManualExecutionStarted(projectId: String, stepId: String, operationId: String, attestation: String): WorkflowStepRecord {
        require(operationId.isNotBlank()) { "Identificador da operação obrigatório." }
        require(attestation.isNotBlank()) { "Confirmação de execução manual obrigatória." }
        val current = get(projectId, stepId)
        check(current.state == WorkflowStepState.WAITING_CONFIRMATION) {
            "A etapa não está aguardando execução manual. Estado atual: ${current.state}."
        }
        check(operationId != current.operationId) { "Cada execução manual precisa de um identificador novo." }
        return save(current.copy(
            state = WorkflowStepState.IN_PROGRESS,
            updatedAt = System.currentTimeMillis(),
            operationId = operationId,
            message = "Usuário declarou que executou o comando manualmente; aguardando diagnóstico atualizado.",
            evidence = "DECLARAÇÃO DE EXECUÇÃO MANUAL: $attestation"
        ))
    }

    /** Records a user's explicit attestation for a manual Console task; not a Google diagnostic. */
    @Synchronized
    fun confirmManualCompletion(projectId: String, stepId: String, userAttestation: String): WorkflowStepRecord {
        require(userAttestation.isNotBlank()) { "Confirmação do usuário obrigatória." }
        val current = get(projectId, stepId)
        check(current.state == WorkflowStepState.IN_PROGRESS) {
            "A etapa manual não está em andamento."
        }
        return save(current.copy(
            state = WorkflowStepState.USER_CONFIRMED,
            updatedAt = System.currentTimeMillis(),
            message = "Usuário declarou a configuração concluída; não verificada por diagnóstico automático.",
            evidence = "CONFIRMAÇÃO MANUAL: $userAttestation"
        ))
    }

    /** Use whenever a timeout, lost connection, or ambiguous response occurs. */
    @Synchronized
    fun markUnknown(projectId: String, stepId: String, message: String): WorkflowStepRecord {
        val current = get(projectId, stepId)
        check(current.state == WorkflowStepState.IN_PROGRESS || current.state == WorkflowStepState.UNKNOWN) {
            "Somente uma operação em andamento pode ficar com resultado desconhecido."
        }
        return save(current.copy(
            state = WorkflowStepState.UNKNOWN,
            updatedAt = System.currentTimeMillis(),
            message = message,
            evidence = ""
        ))
    }

    /** Completion requires fresh, project-matched evidence from an external diagnostic. */
    @Synchronized
    fun reconcileCompleted(projectId: String, stepId: String, evidence: String): WorkflowStepRecord {
        require(evidence.isNotBlank()) { "Evidência de diagnóstico obrigatória." }
        val current = get(projectId, stepId)
        check(current.state == WorkflowStepState.IN_PROGRESS || current.state == WorkflowStepState.UNKNOWN) {
            "A etapa não está aguardando reconciliação."
        }
        return save(current.copy(
            state = WorkflowStepState.COMPLETED,
            updatedAt = System.currentTimeMillis(),
            message = "Resultado confirmado por diagnóstico.",
            evidence = evidence
        ))
    }

    /**
     * Resolves an UNKNOWN operation only when a fresh diagnostic for the same
     * Project ID explicitly proves the target resource is absent. This does not
     * delete the old operation record or silently issue another command.
     */
    @Synchronized
    fun reconcileAbsent(projectId: String, stepId: String, evidence: String, userAcknowledged: Boolean): WorkflowStepRecord {
        require(evidence.isNotBlank()) { "Evidência de diagnóstico obrigatória." }
        check(userAcknowledged) { "A confirmação explícita do usuário é obrigatória." }
        val current = get(projectId, stepId)
        check(current.state == WorkflowStepState.UNKNOWN) { "Somente uma operação desconhecida pode ser reconciliada como ausente." }
        return save(current.copy(
            state = WorkflowStepState.FAILED,
            updatedAt = System.currentTimeMillis(),
            message = "Diagnóstico recente indicou recurso ausente. Nova tentativa exige confirmação separada.",
            evidence = evidence
        ))
    }

    @Synchronized
    fun markFailed(projectId: String, stepId: String, message: String): WorkflowStepRecord {
        val current = get(projectId, stepId)
        check(current.state == WorkflowStepState.IN_PROGRESS) { "Somente uma operação em andamento pode ser marcada como falha." }
        return save(current.copy(
            state = WorkflowStepState.FAILED,
            updatedAt = System.currentTimeMillis(),
            message = message,
            evidence = ""
        ))
    }

    /** Append-only audit event, scoped to the selected project and operation. */
    @Synchronized
    fun appendAuditEvent(
        projectId: String,
        stepId: String,
        state: String,
        operationId: String,
        message: String,
        evidence: String = ""
    ) {
        require(projectId.isNotBlank()) { "Project ID obrigatório para registrar o histórico." }
        require(stepId.isNotBlank()) { "Etapa obrigatória para registrar o histórico." }
        require(state.isNotBlank()) { "Estado obrigatório para registrar o histórico." }
        val history = readHistory()
        history.put(JSONObject().apply {
            put("projectId", projectId)
            put("stepId", stepId)
            put("state", state)
            put("updatedAt", System.currentTimeMillis())
            put("operationId", operationId)
            put("message", message)
            put("evidence", evidence)
        })
        persistHistory(history)
    }

    /** Chronological audit trail for exactly one Project ID, newest first. */
    @Synchronized
    fun historyForProject(projectId: String): List<WorkflowHistoryEntry> {
        if (projectId.isBlank()) return emptyList()
        val history = readHistory()
        val result = mutableListOf<WorkflowHistoryEntry>()
        for (i in 0 until history.length()) {
            val item = history.optJSONObject(i) ?: continue
            if (item.optString("projectId") == projectId) {
                result += WorkflowHistoryEntry(
                    projectId = projectId,
                    stepId = item.optString("stepId"),
                    state = item.optString("state"),
                    updatedAt = item.optLong("updatedAt"),
                    operationId = item.optString("operationId"),
                    message = item.optString("message"),
                    evidence = item.optString("evidence")
                )
            }
        }
        return result.sortedByDescending { it.updatedAt }
    }

    @Synchronized
    fun allForProject(projectId: String): List<WorkflowStepRecord> {
        if (projectId.isBlank()) return emptyList()
        val records = readRecords()
        val result = mutableListOf<WorkflowStepRecord>()
        val keys = records.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val item = records.optJSONObject(key) ?: continue
            if (item.optString("projectId") == projectId) {
                val stepId = item.optString("stepId")
                if (stepId.isNotBlank()) result += item.toRecord(projectId, stepId)
            }
        }
        return result.sortedBy { it.stepId }
    }

    private fun save(record: WorkflowStepRecord): WorkflowStepRecord {
        val records = readRecords()
        records.put(recordKey(record.projectId, record.stepId), record.toJson())
        val history = readHistory()
        history.put(JSONObject().apply {
            put("projectId", record.projectId)
            put("stepId", record.stepId)
            put("state", record.state.name)
            put("updatedAt", record.updatedAt)
            put("operationId", record.operationId)
            put("message", record.message)
            put("evidence", record.evidence)
        })
        prefs.edit().putString(KEY_RECORDS, records.toString()).apply()
        persistHistory(history)
        return record
    }

    private fun readHistory(): JSONArray {
        val raw = prefs.getString(KEY_HISTORY, "[]") ?: "[]"
        return try {
            JSONArray(raw)
        } catch (error: Exception) {
            throw IllegalStateException(
                "O histórico detalhado do GEIVIA está inválido. Os registros foram preservados e novas operações foram bloqueadas.",
                error
            )
        }
    }

    private fun persistHistory(history: JSONArray) {
        val bounded = JSONArray()
        for (i in (history.length() - 500).coerceAtLeast(0) until history.length()) {
            bounded.put(history.getJSONObject(i))
        }
        prefs.edit().putString(KEY_HISTORY, bounded.toString()).apply()
    }

    private fun readRecords(): JSONObject {
        val raw = prefs.getString(KEY_RECORDS, "{}") ?: "{}"
        return try {
            JSONObject(raw)
        } catch (error: Exception) {
            // Never interpret corrupted progress as an empty workflow: that could
            // make an unfinished cloud operation appear safe to start again.
            throw IllegalStateException(
                "O histórico local do GEIVIA está inválido. As operações foram bloqueadas para evitar duplicidade. Preserve os dados e faça a recuperação antes de continuar.",
                error
            )
        }
    }

    private fun recordKey(projectId: String, stepId: String) = "$projectId::$stepId"

    private fun WorkflowStepRecord.toJson() = JSONObject().apply {
        put("projectId", projectId)
        put("stepId", stepId)
        put("state", state.name)
        put("updatedAt", updatedAt)
        put("operationId", operationId)
        put("message", message)
        put("evidence", evidence)
        put("preparedAt", preparedAt)
    }

    private fun JSONObject.toRecord(projectId: String, stepId: String) = WorkflowStepRecord(
        projectId = projectId,
        stepId = stepId,
        state = runCatching { WorkflowStepState.valueOf(optString("state")) }.getOrDefault(WorkflowStepState.UNKNOWN),
        updatedAt = optLong("updatedAt"),
        operationId = optString("operationId"),
        message = optString("message"),
        evidence = optString("evidence"),
        preparedAt = optLong("preparedAt", optLong("updatedAt"))
    )
}
