package com.intellij.ide.starter.driver.reused

import com.intellij.ide.starter.testContextCache.CachedProjectConfiguration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.nio.file.Files

/** Verifies that a `@Nested` class of a [ReusedRun] class fails before its tests start an IDE. */
class ReusedRunNestedClassTest {
  @Test
  fun `nested class is rejected`() {
    val summary = executeReusedIdeRecoveryScenario(NestedClassScenario::class.java)

    assertEquals(0, summary.testsStartedCount)
    val failure = summary.failures.single().exception
    assertTrue(failure.message.orEmpty().contains("does not support @" + Nested::class.java.simpleName), failure.toString())
  }
}

@ReusedRun
@Disabled("Executed only through ReusedRunNestedClassTest")
class NestedClassScenario {
  companion object {
    @ReusedProject
    private val project: CachedProjectConfiguration =
      cachedProjectConfiguration(Files.createTempDirectory("reused-nested-class-project"))
  }

  @Nested
  inner class Inner {
    @Test
    fun `never runs`() {
      throw AssertionError("A @Nested class must be rejected before its tests run")
    }
  }
}
