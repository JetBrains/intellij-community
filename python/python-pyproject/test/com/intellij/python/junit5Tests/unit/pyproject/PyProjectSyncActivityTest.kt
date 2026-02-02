package com.intellij.python.junit5Tests.unit.pyproject

import com.intellij.ide.impl.OpenProjectTask
import com.intellij.openapi.components.service
import com.intellij.python.pyproject.model.internal.autoImportBridge.PyProjectAutoImportService
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.delay
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

@TestApplication
internal class PyProjectSyncActivityTest {
  private val projectFixture =
    projectFixture(openProjectTask = OpenProjectTask().copy(isProjectCreatedWithWizard = true), openAfterCreation = true)


  @Test
  fun testNoAutoRebuildForWizardBasedProject(): Unit = timeoutRunBlocking {
    delay(1.seconds)
    projectFixture.get().service<PyProjectAutoImportService>().start()
  }
}
