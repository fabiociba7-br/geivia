package com.geivia.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceCompatibilityTest {
    @Test fun detectsXiaomiRedmiAndPoco() {
        assertTrue(DeviceCompatibility.isXiaomiFamily("Xiaomi", "Xiaomi"))
        assertTrue(DeviceCompatibility.isXiaomiFamily("Redmi", "redmi"))
        assertTrue(DeviceCompatibility.isXiaomiFamily("POCO", "POCO"))
    }

    @Test fun doesNotClassifyOtherManufacturersAsXiaomi() {
        assertFalse(DeviceCompatibility.isXiaomiFamily("samsung", "samsung"))
        assertFalse(DeviceCompatibility.isXiaomiFamily("Google", "pixel"))
        assertFalse(DeviceCompatibility.isXiaomiFamily("", ""))
    }

    @Test fun includesHyperOsSafeGuidance() {
        val guidance = DeviceCompatibility.hyperOsGuidance().joinToString(" ")
        assertTrue(guidance.contains("Inicialização automática"))
        assertTrue(guidance.contains("root"))
        assertTrue(guidance.contains("Importar arquivos"))
    }
}
