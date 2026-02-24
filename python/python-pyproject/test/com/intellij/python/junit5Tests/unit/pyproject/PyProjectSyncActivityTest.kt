package com.intellij.python.junit5Tests.unit.pyproject

import com.intellij.ide.impl.OpenProjectTask
import com.intellij.openapi.components.service
import com.intellij.python.pyproject.model.PyProjectModelSettings
import com.intellij.python.pyproject.model.internal.autoImportBridge.PyProjectAutoImportService
import com.intellij.python.pyproject.model.internal.platformBridge.PyProjectSyncActivity
import com.intellij.python.pyproject.model.internal.startAutoImportIfNeeded
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

@TestApplication
internal class PyProjectSyncActivityTest {
  private val project by
  projectFixture(openProjectTask = OpenProjectTask().copy(isProjectCreatedWithWizard = true), openAfterCreation = true)


  @ParameterizedTest
  @ValueSource(booleans = [true, false])
  fun testNoAutoRebuildForWizardBasedProject(enableAutoImport: Boolean): Unit = timeoutRunBlocking {
    val settings = project.service<PyProjectModelSettings>()
    val oldValue = settings.usePyprojectToml
    settings.usePyprojectToml = enableAutoImport
    // Setting this var starts import automatically, so we stop it check that code starts it in tests
    project.service<PyProjectAutoImportService>().stop()
    try {
      val sut = project.service<PyProjectAutoImportService>()
      PyProjectSyncActivity().execute(project)
      assertFalse(sut.initialized, "Newly opened project shouldn't lead to autoimport ")
      startAutoImportIfNeeded(project)
      if (enableAutoImport) {
        assertTrue(sut.initialized, "Auto import must be started when called manually")
      }
      else {
        assertFalse(sut.initialized, "Auto import started, even though was disabled")
      }
      sut.stop()
      assertFalse(sut.initialized, "Autoimport must be stopped")
    }
    finally {
      settings.usePyprojectToml = oldValue
    }
  }
}
