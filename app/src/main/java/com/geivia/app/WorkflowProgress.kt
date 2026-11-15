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
    val evidence: String
)

class WorkflowProgressStore(private val prefs: SharedPreferences) {
    companion object {
        private const val KEY_RECORDS = "workflow_step_records_v1"
        private const val KEY_HISTORY = "workflow_step_history_v1"
    }

    /** Returns a safe diagnostic without clearing or rewriting corrupted persisted data. */
    @Synchronized
    fun healthError(): String? = runCatching {
        readRecords()
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
            projectId, stepId, WorkflowStepState.NOT_STARTED, 0L, "", "", ""
        )
    }

    /** Persists a review gate. This does not mean the external operation ran. */
    @Synchronized
    fun waitForConfirmation(projectId: String, stepId: String, message: String): WorkflowStepRecord {
        val current = get(projectId, stepId)
        check(current.state != WorkflowStepState.UNKNOWN) {
            "A operação anterior tem resultado desconhecido. Reconcilie-a antes de continuar."
        }
        check(current.state != WorkflowStepState.COMPLETED) {
            "Esta etapa já foi confirmada como concluída."
        }
        return save(current.copy(
            state = WorkflowStepState.WAITING_CONFIRMATION,
            updatedAt = System.currentTimeMillis(),
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
        return save(current.copy(
            state = WorkflowStepState.IN_PROGRESS,
            updatedAt = System.currentTimeMillis(),
            operationId = operationId,
            message = message,
            evidence = ""
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
        val history = runCatching { JSONArray(prefs.getString(KEY_HISTORY, "[]")) }.getOrDefault(JSONArray())
        history.put(JSONObject().apply {
            put("projectId", record.projectId)
            put("stepId", record.stepId)
            put("state", record.state.name)
            put("updatedAt", record.updatedAt)
            put("operationId", record.operationId)
            put("message", record.message)
        })
        // Keep a bounded local audit trail.
        val bounded = JSONArray()
        for (i in (history.length() - 200).coerceAtLeast(0) until history.length()) {
            bounded.put(history.getJSONObject(i))
        }
        prefs.edit().putString(KEY_RECORDS, records.toString())
            .putString(KEY_HISTORY, bounded.toString()).apply()
        return record
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
    }

    private fun JSONObject.toRecord(projectId: String, stepId: String) = WorkflowStepRecord(
        projectId = projectId,
        stepId = stepId,
        state = runCatching { WorkflowStepState.valueOf(optString("state")) }.getOrDefault(WorkflowStepState.UNKNOWN),
        updatedAt = optLong("updatedAt"),
        operationId = optString("operationId"),
        message = optString("message"),
        evidence = optString("evidence")
    )
}
