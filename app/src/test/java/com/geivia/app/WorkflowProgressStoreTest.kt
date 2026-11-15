package com.geivia.app

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkflowProgressStoreTest {
    private fun store() = WorkflowProgressStore(InMemorySharedPreferences())

    @Test
    fun unknownOperationCannotBeRetriedUntilSameProjectIsReconciled() {
        val store = store()
        store.waitForConfirmation("project-one", "create", "Review")
        store.confirmAndStart("project-one", "create", "op-one", "Started")
        store.markUnknown("project-one", "create", "Timeout")

        val blocked = org.junit.Assert.assertThrows(IllegalStateException::class.java) {
            store.confirmAndStart("project-one", "create", "op-two", "Must not retry")
        }
        assertTrue(blocked.message.orEmpty().contains("não está liberada"))

        store.reconcileAbsent("project-one", "create", "Fresh describe returned NOT_FOUND", true)
        assertEquals(WorkflowStepState.FAILED, store.get("project-one", "create").state)

        store.waitForConfirmation("project-one", "create", "Explicit new review")
        store.confirmAndStart("project-one", "create", "op-two", "Second operation after reconciliation")
        store.reconcileCompleted("project-one", "create", "Same Project ID is now ACTIVE")
        assertEquals(WorkflowStepState.COMPLETED, store.get("project-one", "create").state)
    }

    @Test
    fun failedOperationRequiresASeparateNewConfirmationBeforeRetry() {
        val store = store()
        store.waitForConfirmation("project-retry", "hosting", "First review")
        store.confirmAndStart("project-retry", "hosting", "op-first", "Started")
        store.markFailed("project-retry", "hosting", "Known failure")

        org.junit.Assert.assertThrows(IllegalStateException::class.java) {
            store.confirmAndStart("project-retry", "hosting", "op-second", "Must remain blocked")
        }

        store.waitForConfirmation("project-retry", "hosting", "User explicitly reviews a new attempt")
        assertEquals(
            WorkflowStepState.IN_PROGRESS,
            store.confirmAndStart("project-retry", "hosting", "op-second", "New confirmed attempt").state
        )
    }

    @Test
    fun manualConfirmationRemainsDistinctFromDiagnosticCompletion() {
        val store = store()
        store.waitForConfirmation("project-two", "hosting", "Review")
        store.confirmAndStart("project-two", "hosting", "hosting-op", "Opened console")
        val result = store.confirmManualCompletion("project-two", "hosting", "User checked public URL")
        assertEquals(WorkflowStepState.USER_CONFIRMED, result.state)
        assertTrue(result.evidence.startsWith("CONFIRMAÇÃO MANUAL:"))
    }

    @Test
    fun recordsAreIsolatedByProjectId() {
        val store = store()
        store.waitForConfirmation("project-a", "api", "Review A")
        store.waitForConfirmation("project-b", "api", "Review B")
        store.confirmAndStart("project-a", "api", "op-a", "Started A")
        assertEquals(WorkflowStepState.IN_PROGRESS, store.get("project-a", "api").state)
        assertEquals(WorkflowStepState.WAITING_CONFIRMATION, store.get("project-b", "api").state)
    }
}

/** Minimal in-memory implementation so state-machine tests need no emulator or network. */
private class InMemorySharedPreferences : SharedPreferences {
    private val values = mutableMapOf<String, Any>()

    override fun getAll(): MutableMap<String, *> = values.toMutableMap()
    override fun getString(name: String?, defValue: String?): String? = values[name.orEmpty()] as? String ?: defValue
    override fun getStringSet(name: String?, defValues: MutableSet<String>?): MutableSet<String>? =
        (values[name.orEmpty()] as? Set<*>)?.filterIsInstance<String>()?.toMutableSet() ?: defValues
    override fun getInt(name: String?, defValue: Int): Int = values[name.orEmpty()] as? Int ?: defValue
    override fun getLong(name: String?, defValue: Long): Long = values[name.orEmpty()] as? Long ?: defValue
    override fun getFloat(name: String?, defValue: Float): Float = values[name.orEmpty()] as? Float ?: defValue
    override fun getBoolean(name: String?, defValue: Boolean): Boolean = values[name.orEmpty()] as? Boolean ?: defValue
    override fun contains(name: String?): Boolean = values.containsKey(name.orEmpty())
    override fun edit(): SharedPreferences.Editor = MemoryEditor()
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

    private inner class MemoryEditor : SharedPreferences.Editor {
        private val changes = mutableMapOf<String, Any?>()
        private var clearAll = false

        override fun putString(name: String?, value: String?): SharedPreferences.Editor = apply { changes[name.orEmpty()] = value }
        override fun putStringSet(name: String?, values: MutableSet<String>?): SharedPreferences.Editor =
            apply { changes[name.orEmpty()] = values?.toMutableSet() }
        override fun putInt(name: String?, value: Int): SharedPreferences.Editor = apply { changes[name.orEmpty()] = value }
        override fun putLong(name: String?, value: Long): SharedPreferences.Editor = apply { changes[name.orEmpty()] = value }
        override fun putFloat(name: String?, value: Float): SharedPreferences.Editor = apply { changes[name.orEmpty()] = value }
        override fun putBoolean(name: String?, value: Boolean): SharedPreferences.Editor = apply { changes[name.orEmpty()] = value }
        override fun remove(name: String?): SharedPreferences.Editor = apply { changes[name.orEmpty()] = null }
        override fun clear(): SharedPreferences.Editor = apply { clearAll = true; changes.clear() }
        override fun commit(): Boolean {
            if (clearAll) values.clear()
            changes.forEach { (key, value) -> if (value == null) values.remove(key) else values[key] = value }
            return true
        }
        override fun apply() { commit() }
    }
}
