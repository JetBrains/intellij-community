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
import com.intellij.python.pyproject.model.internal.addPyProject.AddPyProjectPresenter
import com.intellij.python.pyproject.model.internal.addPyProject.projectCreationModel
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
import org.junit.jupiter.params.provider.ValueSource
import kotlin.io.path.readText

@TestOnly
abstract class AddPyProjectPresenterTestBase protected constructor(
  private val toolName: @NlsSafe String,
  private val additionalChecks: AdditionalChecks?,
) {
  private val sdkFixture by pySdkFixture()
  private val pathFixture = tempPathFixture()
  private val projectFixture = projectFixture(pathFixture)
  private val module by projectFixture
    .moduleFixture(pathFixture, addPathToSourceRoot = true, moduleTypeId = PyNames.MODULE)

  @ParameterizedTest
  @ValueSource(strings = ["myProject", "my project"])
  fun testNewProject(projectName: @NlsSafe String): Unit = timeoutRunBlocking {
    val sdk = sdkFixture.sdk
    val additionalData = additionalChecks?.additionalData
    additionalData?.let { additionalDataToSet ->
      writeAction {
        val m = sdk.sdkModificator
        m.sdkAdditionalData = additionalDataToSet
        m.commitChanges()
      }
    }

    val sut = ensureActionIsVisibleAndGetPresenter()
    sut.projectName = projectName
    sut.createProject().orThrow()
    val baseTempDir = pathFixture.get().resolve(sut.projectName)
    val toml = PyProjectToml.parse(baseTempDir.resolve("pyproject.toml").readText())!!
    Assertions.assertEquals(PyPackageName.normalizeProjectName(projectName), toml.project.name)
    assertThat(sut.actionText).contains(toolName)
    additionalChecks?.fileNames?.forEach { fileName ->
      assertThat(baseTempDir.resolve(fileName)).isRegularFile
    }
  }

  private suspend fun ensureActionIsVisibleAndGetPresenter(): AddPyProjectPresenter {
    val sdk = sdkFixture.sdk
    withSdkConfigurationLock(projectFixture.get()) {
      withContext(Dispatchers.IO) {
        module.pythonSdk = sdk
      }
    }

    val action = ActionManager.getInstance().getAction("AddPyProject") as AddPyProjectAction
    val context = SimpleDataContext.builder()
      .add(CommonDataKeys.VIRTUAL_FILE, pathFixture.get().refreshAndGetVirtualDirectory())
      .add(LangDataKeys.MODULE, module)
      .add(CommonDataKeys.PROJECT, projectFixture.get())
      .build()
    val event = AnActionEvent.createEvent(context, null, "..", ActionUiKind.POPUP, null)
    action.update(event)
    Assertions.assertTrue(event.presentation.isEnabledAndVisible)
    assertThat(event.presentation.text).contains(toolName)
    return event.projectCreationModel!!
  }

  protected data class AdditionalChecks(val additionalData: PythonSdkAdditionalData, val fileNames: Set<String>) {
    init {
      check(fileNames.isNotEmpty())
    }
  }
}
