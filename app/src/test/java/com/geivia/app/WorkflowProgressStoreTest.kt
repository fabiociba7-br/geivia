package com.geivia.app

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class WorkflowProgressStoreTest {
    private fun store(): WorkflowProgressStore {
        val context: Context = org.robolectric.RuntimeEnvironment.getApplication()
        return WorkflowProgressStore(
            context.getSharedPreferences("workflow-test-${UUID.randomUUID()}", Context.MODE_PRIVATE)
        )
    }

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
