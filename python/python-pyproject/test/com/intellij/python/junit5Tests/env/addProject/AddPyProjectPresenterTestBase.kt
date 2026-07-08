package com.intellij.python.junit5Tests.env.addProject

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.util.NlsSafe
import com.intellij.python.junit5Tests.framework.env.pySdkFixture
import com.intellij.python.pyproject.PyProjectToml
import com.intellij.python.pyproject.model.internal.addPyProject.AddPyProjectAction
import com.intellij.python.pyproject.model.internal.addPyProject.ConvertToPyProjectAction
import com.intellij.python.pyproject.model.internal.addPyProject.PyProjectPresenter
import com.intellij.python.pyproject.model.internal.addPyProject.projectCreationPresenter
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.testFramework.utils.vfs.refreshAndGetVirtualDirectory
import com.jetbrains.python.PyNames
import com.jetbrains.python.packaging.PyPackageName
import com.jetbrains.python.sdk.PythonSdkAdditionalData
import com.jetbrains.python.sdk.pythonSdk
import com.jetbrains.python.sdk.withSdkConfigurationLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.annotations.TestOnly
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.NullSource
import org.junit.jupiter.params.provider.ValueSource
import kotlin.io.path.pathString
import kotlin.io.path.readText

@TestOnly
abstract class AddPyProjectPresenterTestBase protected constructor(
  private val toolName: @NlsSafe String,
  private val additionalChecks: AdditionalChecks?,
  // For poetry bug workaround https://github.com/python-poetry/poetry/issues/10974
  private val spacesInProjectNamesLeadToBug: Boolean = false
) {
  private val sdkFixture by pySdkFixture()
  private val pathFixture = tempPathFixture()
  private val projectFixture = projectFixture(pathFixture)
  private val module by projectFixture.moduleFixture(pathFixture, addPathToSourceRoot = true, moduleTypeId = PyNames.MODULE)

  /**
   * There are two modes: with [projectName] ([AddPyProjectAction] is used) and when [projectName] is `null` -> [ConvertToPyProjectAction]
   */
  @ParameterizedTest
  @ValueSource(strings = ["myProject", "my project"])
  @NullSource
  fun testPyProject(projectName: @NlsSafe String?): Unit = timeoutRunBlocking {

    val sdk = sdkFixture.sdk
    val additionalData = additionalChecks?.additionalData
    additionalData?.let { additionalDataToSet ->
      writeAction {
        val m = sdk.sdkModificator
        m.sdkAdditionalData = additionalDataToSet
        m.commitChanges()
      }
    }

    val sut = ensureActionIsVisibleAndGetPresenter(forNewProject = projectName != null)
    if (projectName != null) {
      sut.projectName = projectName
    }
    sut.createProject().orThrow()
    val tempDir = pathFixture.get()
    // For no projectName we create project in the same dir
    val projectDir = if (projectName != null) tempDir.resolve(sut.projectName) else tempDir
    val toml = PyProjectToml.parse(projectDir.resolve("pyproject.toml").readText())!!
    val expectedProjectName = projectName ?: projectDir.fileName.pathString
    if (' ' !in expectedProjectName || !spacesInProjectNamesLeadToBug) {
      Assertions.assertEquals(PyPackageName.normalizeProjectName(expectedProjectName), toml.project.name)
    }
    assertThat(sut.actionText).contains(toolName)
    additionalChecks?.fileNames?.forEach { fileName ->
      assertThat(projectDir.resolve(fileName)).isRegularFile
    }
  }

  private suspend fun ensureActionIsVisibleAndGetPresenter(forNewProject: Boolean): PyProjectPresenter {
    val sdk = sdkFixture.sdk
    withSdkConfigurationLock(projectFixture.get()) {
      withContext(Dispatchers.IO) {
        module.pythonSdk = sdk
      }
    }

    val actionManager = ActionManager.getInstance()
    val action = if (forNewProject) {
      actionManager.getAction("AddPyProject") as AddPyProjectAction
    }
    else {
      actionManager.getAction("ConvertToPyProject") as ConvertToPyProjectAction
    }

    val context = SimpleDataContext.builder().add(CommonDataKeys.VIRTUAL_FILE, pathFixture.get().refreshAndGetVirtualDirectory())
      .add(LangDataKeys.MODULE, module).add(CommonDataKeys.PROJECT, projectFixture.get()).build()
    val event = AnActionEvent.createEvent(context, null, "..", ActionUiKind.POPUP, null)
    action.update(event)
    Assertions.assertTrue(event.presentation.isEnabledAndVisible)
    assertThat(event.presentation.text).contains(toolName)
    return event.projectCreationPresenter(forNewProject)!!
  }

  protected data class AdditionalChecks(val additionalData: PythonSdkAdditionalData, val fileNames: Set<String>)
}
