package com.intellij.python.junit5Tests.unit.pyproject

import com.intellij.ide.impl.OpenProjectTask
import com.intellij.openapi.components.service
import com.intellij.python.pyproject.model.internal.autoImportBridge.PyProjectAutoImportService
import com.intellij.python.pyproject.model.internal.platformBridge.PyProjectSyncActivity
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

@TestApplication
internal class PyProjectSyncActivityTest {
  private val project by
  projectFixture(openProjectTask = OpenProjectTask().copy(isProjectCreatedWithWizard = true), openAfterCreation = true)


  @Test
  fun testNoAutoRebuildForWizardBasedProject(): Unit = timeoutRunBlocking {
    PyProjectSyncActivity().execute(project)
    assertFalse(project.service<PyProjectAutoImportService>().initialized, "Newly opened project shouldn't lead to autoimport ")
  }
}
