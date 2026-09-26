package com.intellij.ide.starter.driver

import com.intellij.driver.sdk.ProjectManager
import com.intellij.driver.sdk.waitFor
import com.intellij.ide.starter.data.IdeaUltimateCases
import com.intellij.ide.starter.driver.engine.runIdeWithDriver
import com.intellij.ide.starter.junit5.hyphenateWithClass
import com.intellij.ide.starter.runner.CurrentTestMethod
import com.intellij.ide.starter.runner.Starter
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.minutes

class IdeRunsInBackgroundTest {

  @Test
  fun usingDriverWithPreconfiguredIde() {
    val context = Starter.newContext(testName = CurrentTestMethod.hyphenateWithClass(), testCase = IdeaUltimateCases.JitPackAndroidExample)
      .skipIndicesInitialization()

    val start = context.runIdeWithDriver()
    start.useDriverAndCloseIde {
      waitFor(errorMessage = { "No opened projects" }, timeout = 1.minutes) {
        service(ProjectManager::class).getOpenProjects().size == 1
      }
    }
  }
}