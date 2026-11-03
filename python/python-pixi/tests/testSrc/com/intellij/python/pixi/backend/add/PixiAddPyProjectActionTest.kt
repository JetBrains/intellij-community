// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pixi.backend.add

import com.intellij.idea.TestFor
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorOpenRequest
import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.python.junit5Tests.framework.env.pyMockSdkFixture
import com.intellij.python.junit5Tests.framework.pyProjectFixture
import com.intellij.python.pixi.backend.sdk.PixiSdkFlavor
import com.intellij.python.pyproject.model.evolution.EvoPyProjectModel
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.testFramework.utils.vfs.refreshAndGetVirtualDirectory
import com.intellij.ui.EditorNotificationProvider
import com.intellij.util.ui.UIUtil
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.project.PyProject.Companion.asPyProject
import com.jetbrains.python.sdk.PythonSdkAdditionalData
import com.jetbrains.python.sdk.flavors.PyFlavorAndData
import com.jetbrains.python.sdk.flavors.PyFlavorData
import com.jetbrains.python.sdk.setPythonSdkForTests
import com.jetbrains.python.sdk.withSdkConfigurationLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import kotlin.io.path.writeText

@TestApplication
@Subsystems.Interpreters
@Layers.Functional
@TestFor(issues = ["PY-91711"])
internal class PixiAddPyProjectActionTest {
  private val pathFixture = tempPathFixture()
  private val projectFixture = projectFixture(pathFixture)
  private val pyProjectFixture = projectFixture.pyProjectFixture(pathFixture)
  private val sdkFixture = projectFixture.pyMockSdkFixture(pathFixture)
  private val module: Module get() = pyProjectFixture.get().residesOnModule

  @ParameterizedTest
  @CsvSource("pixi.toml, 1", "pyproject.toml, 1", "main.py, 0")
  fun `the manifest editor banner contains one export action`(fileName: String, expectedCount: Int): Unit = timeoutRunBlocking {
    configureInterpreter()
    pathFixture.get().resolve(fileName).writeText(if (fileName.endsWith(".toml")) "[project]\nname = 'example'\n" else "")
    val project = projectFixture.get()
    val directory = pathFixture.get().refreshAndGetVirtualDirectory()
    directory.refresh(false, true)
    val file = checkNotNull(directory.findChild(fileName))
    val editorManager = FileEditorManager.getInstance(project)
    val editor = editorManager.openFile(file, FileEditorOpenRequest.defaults()).allEditors.single()
    try {
      val notifications = readAction {
        EditorNotificationProvider.EP_NAME.getExtensions(project).mapNotNull { it.collectNotificationData(project, file) }
      }
      val exportAction = ActionManager.getInstance().getAction("Python.Pixi.ExportEnvironment")
      val event = event(file)
      val exportActions = withContext(Dispatchers.EDT) {
        notifications.mapNotNull { it.apply(editor) }.flatMap { component ->
          UIUtil.uiTraverser(component).toList().filterIsInstance<ActionToolbar>()
            .flatMap { it.actionGroup.getChildren(event).toList() }
        }.filter { it === exportAction }
      }

      assertThat(exportActions).hasSize(expectedCount)
    }
    finally {
      withContext(Dispatchers.EDT) { editorManager.closeFile(file) }
    }
  }

  @ParameterizedTest
  @CsvSource("true, pixi.toml, true", "true, pyproject.toml, true", "true, main.py, false", "false, pyproject.toml, false")
  fun `the export action is available only for a Pixi manifest`(
    pixi: Boolean, fileName: String, available: Boolean,
  ): Unit = timeoutRunBlocking {
    configureInterpreter(pixi)
    pathFixture.get().resolve(fileName).writeText(if (fileName.endsWith(".toml")) "[project]\nname = 'example'\n" else "")
    val directory = pathFixture.get().refreshAndGetVirtualDirectory()
    directory.refresh(false, true)
    val psi = readAction { PsiManager.getInstance(projectFixture.get()).findFile(checkNotNull(directory.findChild(fileName))) }
    val context = SimpleDataContext.builder()
      .add(CommonDataKeys.PSI_FILE, psi)
      .add(CommonDataKeys.PROJECT, projectFixture.get())
      .build()
    val event = AnActionEvent.createEvent(context, null, "test", ActionUiKind.POPUP, null)
    val actionManager = ActionManager.getInstance()
    val action = actionManager.getAction("Python.Pixi.ExportEnvironment")

    readAction { action.update(event) }

    assertThat(event.presentation.isEnabledAndVisible).isEqualTo(available)
    val group = actionManager.getAction("PythonPackageManagerActions") as ActionGroup
    assertThat(group.getChildren(event)).contains(action)
  }

  @ParameterizedTest
  @CsvSource(
    "AddPyProject, false, true, New Project with Pixi",
    "AddPyProject, true, true, New Project with Pixi",
    "ConvertToPyProject, false, true, Convert to Project with Pixi",
    "ConvertToPyProject, true, true, Convert to Project with Pixi",
    "AddPyProject, false, false, New Project with pip",
    "ConvertToPyProject, true, false, Convert to Project with pip",
  )
  fun `the actions name the selected interpreter tool`(
    actionId: String, selectFile: Boolean, pixi: Boolean, expectedText: String,
  ): Unit = timeoutRunBlocking {
    configureInterpreter(pixi)
    val event = event(selection(selectFile))

    readAction { ActionManager.getInstance().getAction(actionId).update(event) }

    assertThat(event.presentation.isEnabledAndVisible).isTrue()
    assertThat(event.presentation.text).isEqualTo(expectedText)
  }

  @ParameterizedTest
  @CsvSource(
    "AddPyProject, false, true",
    "AddPyProject, true, true",
    "ConvertToPyProject, false, false",
    "ConvertToPyProject, true, false",
  )
  fun `an existing manifest blocks conversion but permits a new child project`(
    actionId: String, selectFile: Boolean, available: Boolean,
  ): Unit = timeoutRunBlocking {
    configureInterpreter()
    pathFixture.get().resolve("pyproject.toml").writeText("[project]\nname = 'existing'\nversion = '1.0'\n")
    val event = event(selection(selectFile))

    readAction { ActionManager.getInstance().getAction(actionId).update(event) }

    assertThat(event.presentation.isVisible).isEqualTo(available)
    assertThat(event.presentation.isEnabled).isEqualTo(available)
    if (available) assertThat(event.presentation.text).isEqualTo("New Project with Pixi")
  }

  @ParameterizedTest
  @CsvSource(
    "AddPyProject, project", "ConvertToPyProject, project",
    "AddPyProject, module", "ConvertToPyProject, module",
    "AddPyProject, selection", "ConvertToPyProject, selection",
    "AddPyProject, interpreter", "ConvertToPyProject, interpreter",
  )
  fun `the actions hide when required context is missing`(actionId: String, missing: String): Unit = timeoutRunBlocking {
    if (missing != "interpreter") configureInterpreter()
    else {
      val pyProject = pyProjectFixture.get()
      val snapshot = EvoPyProjectModel.getInstance(projectFixture.get()).snapshot()
      assertThat(snapshot.forPyProject(pyProject)).isNotNull()
      assertThat(snapshot.forPyProject(pyProject)?.interpreter).isNull()
    }
    val event = event(selection(false), missing)

    readAction { ActionManager.getInstance().getAction(actionId).update(event) }

    assertThat(event.presentation.isVisible).isFalse()
    assertThat(event.presentation.isEnabled).isFalse()
  }

  @ParameterizedTest
  @CsvSource("AddPyProject, false, New Project with Pixi", "ConvertToPyProject, true, Convert to Project with Pixi")
  fun `the actions accept a PSI selection without a virtual file in the context`(
    actionId: String, selectFile: Boolean, expectedText: String,
  ): Unit = timeoutRunBlocking {
    configureInterpreter()
    val selectedFile = selection(selectFile)
    val psi = readAction {
      val manager = PsiManager.getInstance(projectFixture.get())
      if (selectFile) manager.findFile(selectedFile) else manager.findDirectory(selectedFile)
    }
    assertThat(psi).isNotNull()
    val context = SimpleDataContext.builder()
      .add(PlatformCoreDataKeys.PSI_ELEMENT, psi)
      .addNull(CommonDataKeys.VIRTUAL_FILE)
      .add(LangDataKeys.MODULE, module)
      .add(CommonDataKeys.PROJECT, projectFixture.get())
      .build()
    val event = AnActionEvent.createEvent(context, null, "test", ActionUiKind.POPUP, null)

    readAction { ActionManager.getInstance().getAction(actionId).update(event) }

    assertThat(event.presentation.isEnabledAndVisible).isTrue()
    assertThat(event.presentation.text).isEqualTo(expectedText)
  }

  private suspend fun configureInterpreter(pixi: Boolean = true) {
    val sdk = sdkFixture.get()
    edtWriteAction {
      val modificator = sdk.sdkModificator
      modificator.sdkAdditionalData = PythonSdkAdditionalData(
        if (pixi) PyFlavorAndData(PyFlavorData.Empty, PixiSdkFlavor) else null,
        pathFixture.get(),
      )
      modificator.commitChanges()
    }
    withSdkConfigurationLock(projectFixture.get()) {
      withContext(Dispatchers.IO) {
        module.setPythonSdkForTests(sdk)
      }
    }
    EvoPyProjectModel.getInstance(projectFixture.get()).awaitInterpreterOf(listOfNotNull(module.asPyProject()))
  }

  private fun selection(selectFile: Boolean): VirtualFile {
    if (selectFile) pathFixture.get().resolve("main.py").writeText("print('keep me')\n")
    val directory = pathFixture.get().refreshAndGetVirtualDirectory()
    directory.refresh(false, true)
    return if (selectFile) checkNotNull(directory.findChild("main.py")) else directory
  }

  private fun event(selection: VirtualFile, missing: String? = null): AnActionEvent {
    val context = SimpleDataContext.builder()
    if (missing != "selection") context.add(CommonDataKeys.VIRTUAL_FILE, selection)
    if (missing == "module") context.addNull(LangDataKeys.MODULE)
    else context.add(LangDataKeys.MODULE, module)
    if (missing != "project") context.add(CommonDataKeys.PROJECT, projectFixture.get())
    return AnActionEvent.createEvent(context.build(), null, "test", ActionUiKind.POPUP, null)
  }
}
