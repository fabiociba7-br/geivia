package com.geivia.app

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class FirebaseDeployPlannerTest {
    @Test
    fun hostingAndStorageTargetsAreScopedIndividually() {
        val config = JSONObject()
            .put("hosting", JSONArray()
                .put(JSONObject().put("target", "web"))
                .put(JSONObject().put("target", "admin")))
            .put("storage", JSONObject().put("target", "uploads"))
        assertEquals(listOf("hosting:web", "hosting:admin", "storage:uploads"), FirebaseDeployPlanner.targets(config))
    }

    @Test
    fun firestoreUsesSupportedCombinedSelectorForRulesAndIndexes() {
        val config = JSONObject().put("firestore", JSONObject()
            .put("rules", "firestore.rules")
            .put("indexes", "firestore.indexes.json"))
        assertEquals(listOf("firestore"), FirebaseDeployPlanner.targets(config))
    }

    @Test
    fun unscopedHostingUsesBroadSelectorAndOtherConfiguredServicesAreIncluded() {
        val config = JSONObject()
            .put("hosting", JSONObject().put("public", "public"))
            .put("functions", JSONArray().put(JSONObject().put("source", "functions")))
            .put("database", JSONObject().put("rules", "database.rules.json"))
            .put("auth", JSONObject())
            .put("dataconnect", JSONObject())
        assertEquals(
            listOf("hosting", "database", "functions", "auth", "dataconnect"),
            FirebaseDeployPlanner.targets(config)
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnsafeDeployTargetNames() {
        FirebaseDeployPlanner.targets(
            JSONObject().put("hosting", JSONObject().put("target", "site;rm -rf"))
        )
    }
}
