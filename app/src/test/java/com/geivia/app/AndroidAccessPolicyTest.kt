package com.geivia.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidAccessPolicyTest {
    @Test
    fun privateWorkspaceDoesNotRequestStoragePermission() {
        assertEquals(AndroidAccessMechanism.APP_PRIVATE_STORAGE, AndroidAccessUseCase.APP_WORKSPACE.mechanism)
        assertFalse(AndroidAccessPolicy.requiresRuntimePermission(AndroidAccessUseCase.APP_WORKSPACE))
    }

    @Test
    fun backupAndProjectImportsUseScopedDocumentPicker() {
        assertEquals(AndroidAccessMechanism.CREATE_DOCUMENT, AndroidAccessUseCase.EXPORT_HISTORY_BACKUP.mechanism)
        assertEquals(AndroidAccessMechanism.OPEN_DOCUMENT, AndroidAccessUseCase.VALIDATE_HISTORY_BACKUP.mechanism)
        assertEquals(AndroidAccessMechanism.OPEN_DOCUMENT, AndroidAccessUseCase.IMPORT_CLOUD_PROJECTS.mechanism)
        assertFalse(AndroidAccessPolicy.requiresRuntimePermission(AndroidAccessUseCase.EXPORT_HISTORY_BACKUP))
        assertFalse(AndroidAccessPolicy.requiresRuntimePermission(AndroidAccessUseCase.IMPORT_CLOUD_PROJECTS))
    }

    @Test
    fun rootAccessIsNotPresentedAsGrantableAndroidPermission() {
        assertFalse(AndroidAccessPolicy.isSupportedWithoutRoot(AndroidAccessUseCase.ROOT_OR_PROTECTED_SYSTEM_ACCESS))
        assertFalse(AndroidAccessPolicy.requiresRuntimePermission(AndroidAccessUseCase.ROOT_OR_PROTECTED_SYSTEM_ACCESS))
        assertTrue(AndroidAccessPolicy.guidance(AndroidAccessUseCase.ROOT_OR_PROTECTED_SYSTEM_ACCESS).contains("não oferece uma permissão comum"))
    }

    @Test
    fun manifestPermissionAllowlistMatchesCurrentFeatures() {
        assertEquals(setOf(
            "android.permission.INTERNET",
            "android.permission.POST_NOTIFICATIONS",
            "android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS"
        ), AndroidAccessPolicy.requiredManifestPermissionsForCurrentFeatures())
    }
}
