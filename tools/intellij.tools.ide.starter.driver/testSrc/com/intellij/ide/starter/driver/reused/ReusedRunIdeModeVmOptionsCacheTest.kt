package com.intellij.ide.starter.driver.reused

import com.intellij.driver.client.Driver
import com.intellij.driver.sdk.jdk.getSystemProperty
import com.intellij.ide.starter.testContextCache.CachedProjectConfiguration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.nio.file.Files

/**
 * Verifies that a cached project configuration launches with its own VM options.
 */
@ReusedRun
class ReusedModeVmOptionsCacheBaseTest {
  companion object {
    @ReusedProject
    private val project = vmOptionsCacheProjectWithProperty("base")
  }

  @Test
  fun `base project sees base VM option`(driver: Driver) {
    assertEquals("base", driver.getSystemProperty(VM_OPTIONS_CACHE_PROPERTY))
  }
}

/**
 * Verifies that a separate cached project configuration launches with its own VM options.
 */
@ReusedRun
class ReusedModeVmOptionsCacheAdditionalSetupTest {
  companion object {
    @ReusedProject
    private val project: CachedProjectConfiguration = vmOptionsCacheProjectWithProperty("additional")
  }

  @Test
  fun `additional setup project sees overridden VM option`(driver: Driver) {
    assertEquals("additional", driver.getSystemProperty(VM_OPTIONS_CACHE_PROPERTY))
  }
}

private const val VM_OPTIONS_CACHE_PROPERTY = "reused.ide.cache.actual.vm.option"

private fun vmOptionsCacheProjectWithProperty(propertyValue: String): CachedProjectConfiguration {
  return cachedProjectConfiguration(Files.createTempDirectory("reused-vm-options-cache-project")) {
    applyVMOptionsPatch {
      addSystemProperty(VM_OPTIONS_CACHE_PROPERTY, propertyValue)
    }
  }
}
