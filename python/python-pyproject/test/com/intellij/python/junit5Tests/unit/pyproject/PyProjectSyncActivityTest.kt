package com.intellij.python.junit5Tests.unit.pyproject

import com.intellij.ide.impl.OpenProjectTask
import com.intellij.openapi.components.service
import com.intellij.python.pyproject.model.PyProjectModelSettings
import com.intellij.python.pyproject.model.internal.autoImportBridge.PyProjectAutoImportService
import com.intellij.python.pyproject.model.internal.platformBridge.PyProjectSyncActivity
import com.intellij.python.pyproject.model.internal.startAutoImportIfNeeded
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.properties.Delegates

@TestApplication
internal class PyProjectSyncActivityTest {
  private val project by
  projectFixture(openProjectTask = OpenProjectTask().copy(isProjectCreatedWithWizard = true), openAfterCreation = true)

  private var usePyprojectTomlOldValue by Delegates.notNull<Boolean>()

  @BeforeEach
  fun setUp() {
    usePyprojectTomlOldValue = project.service<PyProjectModelSettings>().usePyprojectToml
  }

  @AfterEach
  fun tearDown() {
    project.service<PyProjectModelSettings>().usePyprojectToml = usePyprojectTomlOldValue
  }

  @Test
  @RegistryKey("intellij.python.pyproject.model", "[off*|on|ask]")
  fun testImportDisabled(): Unit = timeoutRunBlocking {
    val sut = callStartAutoImport(userEnabledImport = true)
    assertFalse(sut.initialized, "Autoimport disabled in registry and shouldn't start")
  }

  @Test
  @RegistryKey("intellij.python.pyproject.model", "[off|on*|ask]")
  fun testImportAlwaysEnabled(): Unit = timeoutRunBlocking {
    val sut = callStartAutoImport(userEnabledImport = false)
    assertTrue(sut.initialized, "Autoimport should start unconditionally")
  }

  @ParameterizedTest
  @ValueSource(booleans = [true, false])
  @RegistryKey("intellij.python.pyproject.model", "[off|on|ask*]")
  fun testNoAutoRebuildForWizardBasedProject(enableAutoImport: Boolean): Unit = timeoutRunBlocking {
    val settings = project.service<PyProjectModelSettings>()
    settings.usePyprojectToml = enableAutoImport
    // Setting this var starts import automatically, so we stop it check that code starts it in tests
    project.service<PyProjectAutoImportService>().stop()
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

  private suspend fun callStartAutoImport(userEnabledImport: Boolean): PyProjectAutoImportService {
    val settings = project.service<PyProjectModelSettings>()
    settings.usePyprojectToml = userEnabledImport
    // Setting this var starts import automatically, so we stop it check that code starts it in tests
    project.service<PyProjectAutoImportService>().stop()
    val sut = project.service<PyProjectAutoImportService>()
    startAutoImportIfNeeded(project)
    return sut
  }
}
