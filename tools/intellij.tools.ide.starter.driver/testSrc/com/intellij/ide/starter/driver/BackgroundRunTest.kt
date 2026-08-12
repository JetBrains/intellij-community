package com.intellij.ide.starter.driver

import com.intellij.ide.starter.driver.engine.throwIdeStartFailure
import com.intellij.ide.starter.models.IDEStartResult
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import org.junit.jupiter.api.Test

class BackgroundRunTest {
  @Test
  fun `startup failure is propagated when IDE exits before driver connects`() {
    val startupFailure = IllegalStateException("Invalid -Xlog option")
    val startResult = CompletableDeferred<IDEStartResult>().apply {
      completeExceptionally(startupFailure)
    }

    val propagatedFailure = shouldThrow<IllegalStateException> {
      throwIdeStartFailure(startResult, "42")
    }

    propagatedFailure.message shouldBe startupFailure.message
  }
}
