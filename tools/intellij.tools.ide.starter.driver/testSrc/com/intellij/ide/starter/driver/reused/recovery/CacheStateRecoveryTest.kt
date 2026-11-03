package com.intellij.ide.starter.driver.reused.recovery

import com.intellij.driver.model.RdTarget
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CacheStateRecoveryTest {
  @Test
  fun `an unchanged cache store has no changed key`() {
    val store = CacheStore(mapOf("com.intellij.SearchEverywhereConfiguration.file-type-list" to "QQ=="), listOf(SEARCH_EVERYWHERE))

    assertEquals(CacheStateChanges(), compareCacheStores(store, store))
  }

  @Test
  fun `each changed key goes to its component, or to no component for CacheTag data`() {
    val whole = CacheFileComponent("JavaScript", "TypeScriptExternalDefinitionsRegistry")
    val before = CacheStore(
      entries = mapOf(
        "com.intellij.SearchEverywhereConfiguration.file-type-list" to "QQ==",
        "com.intellij.editorconfig.encoding" to "QQ==",
      ),
      components = listOf(SEARCH_EVERYWHERE),
    )
    val after = CacheStore(
      entries = mapOf("JavaScript.TypeScriptExternalDefinitionsRegistry" to "Qg==", "com.intellij.editorconfig.encoding" to "Qg=="),
      components = listOf(SEARCH_EVERYWHERE, whole),
    )

    val changes = compareCacheStores(before, after)

    assertEquals(
      mapOf(
        "JavaScript.TypeScriptExternalDefinitionsRegistry" to whole,
        "com.intellij.SearchEverywhereConfiguration.file-type-list" to SEARCH_EVERYWHERE,
        "com.intellij.editorconfig.encoding" to null,
      ),
      changes.changedKeys,
    )
    assertEquals(setOf(whole, SEARCH_EVERYWHERE), changes.changedComponents)
  }

  @Test
  fun `the longer key prefix wins, and a longer name is another component`() {
    val short = CacheFileComponent("p", "A")
    val long = CacheFileComponent("p", "A.B")

    val after = CacheStore(mapOf("p.A.B.x" to "QQ==", "p.AB" to "QQ=="), listOf(short, long))

    val changes = compareCacheStores(CacheStore(emptyMap(), emptyList()), after)

    assertEquals(mapOf("p.A.B.x" to long, "p.AB" to null), changes.changedKeys)
  }

  @Test
  fun `a cache store that recovery cannot list requires a restart`() {
    val empty = CacheStore(emptyMap(), emptyList())

    assertEquals("recovery cannot list the cache store at the IDE start", compareCacheStores(null, empty).restartReason)
    assertEquals("recovery cannot list the cache store after the test", compareCacheStores(empty, null).restartReason)
  }

  @Test
  fun `a restart reason of one target requires a restart of the plan`() {
    val plan = SettingsRecoveryPlan(
      changedFileSpecs = setOf("editor.xml"),
      cacheStateChanges = mapOf(
        RdTarget.DEFAULT to CacheStateChanges(changedKeys = mapOf("com.intellij.editorconfig.encoding" to null)),
        RdTarget.BACKEND to CacheStateChanges(restartReason = "cannot reload"),
      ),
    )

    assertTrue(plan.requiresRestart)
    assertFalse(plan.copy(cacheStateChanges = plan.cacheStateChanges - RdTarget.BACKEND).requiresRestart)
  }
}

private val SEARCH_EVERYWHERE = CacheFileComponent("com.intellij", "SearchEverywhereConfiguration")
