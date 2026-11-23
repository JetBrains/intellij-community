package com.intellij.ide.starter

import com.intellij.ide.starter.config.ConfigurationStorage
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.kodein.di.DI
import org.kodein.di.direct

class ConfigurationStorageTest {
  @Test
  fun `reset restores defaults and drops other keys`() {
    val storage = ConfigurationStorage(DI {}.direct, mapOf("a" to "1", "b" to "2"))
    storage.put("a", "changed")
    storage.put("extra", "x")

    storage.resetToDefault()

    storage.getAll().shouldBe(mapOf("a" to "1", "b" to "2"))
  }
}
