package com.intellij.ide.starter.driver.engine

import com.intellij.ide.starter.runner.CurrentTestMethod
import com.intellij.ide.starter.runner.TestMethod
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

internal class CurrentTestLogSynchronizerTest {
  @Test
  @Timeout(30)
  fun `disconnected driver removes its listener before the next test`() {
    var connectionAttempts = 0
    val synchronizer = CurrentTestLogSynchronizer(
      isConnected = {
        connectionAttempts++
        false
      },
      syncLogDir = { error("must not synchronize a disconnected driver") },
    )

    try {
      synchronizer.start()
      assertEquals(1, connectionAttempts)
      CurrentTestMethod.set(
        TestMethod(
          name = "nextTest",
          displayName = "next test",
          testClass = javaClass,
        )
      )
      CurrentTestMethod.publishToListeners()
      assertEquals(1, connectionAttempts)
    }
    finally {
      CurrentTestMethod.set(null)
    }
  }
}
