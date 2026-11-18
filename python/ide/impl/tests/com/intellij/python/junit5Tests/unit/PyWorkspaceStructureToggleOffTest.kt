// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.doNotEnableExternalStorageByDefaultInTestsSuspend
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.writeText
import com.intellij.project.stateStore
import com.intellij.pycharm.community.ide.impl.configuration.interpreter.PyWorkspaceStructureConfigurable
import com.intellij.python.pyproject.PY_PROJECT_TOML
import com.intellij.python.pyproject.model.api.isPyProjectTomlBased
import com.intellij.python.pyproject.model.internal.platformBridge.rebuildPyProjectModelForTest
import com.intellij.python.pyproject.model.usePyprojectToml
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.registryKeyFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.awt.Container
import javax.swing.JCheckBox
import javax.swing.JComponent
import kotlin.time.Duration.Companion.minutes

/**
 * Turning "Auto-configure project workspace from pyproject.toml" off and closing the dialog with OK
 * must leave no pyproject-generated module behind (PY-94202). OK calls `apply()` and then disposes
 * the page in the same EDT event, and `disposeUIResources` cancels the page's session scope, so a
 * cleanup that `apply()` only schedules there never reaches the workspace model: the setting is
 * persisted as off while every pyproject module survives, and the sync service that would repair
 * them is already stopped.
 *
 * The cleanup contract itself — which modules go and which are demoted — is pinned by
 * `PyProjectCleanupModulesTest`. This class pins only that `apply()` has finished it before it
 * returns, so the dispose that follows has nothing left to cancel.
 */
@TestApplication
internal class PyWorkspaceStructureToggleOffTest {

  // The feature is off by default in the registry, which hides the checkbox and reports the
  // setting as off, so the toggle transition under test could not happen at all.
  private val pyProjectModelKey = registryKeyFixture("intellij.python.pyproject.model") { setValue("on") }

  private val projectFixture = projectFixture()

  @Test
  fun `apply completes the cleanup before the dialog disposes the page`(): Unit = timeoutRunBlocking(2.minutes) {
    pyProjectModelKey.get()
    val project = projectFixture.get()
    val baseline = moduleNames(project)

    val root = VirtualFileManager.getInstance().refreshAndFindFileByNioPath(project.stateStore.projectBasePath)!!
    edtWriteAction {
      root.writePyProjectToml("root")
      root.createChildDirectory(this, "sub").writePyProjectToml("child")
    }
    doNotEnableExternalStorageByDefaultInTestsSuspend { rebuildPyProjectModelForTest(project) }

    assertThat(pyProjectModuleNames(project))
      .describedAs("Precondition: the sync must produce the pyproject modules the cleanup then has to remove")
      .isNotEmpty
    assertThat(project.usePyprojectToml)
      .describedAs("Precondition: the toggle must start out on, or `apply` sees no transition")
      .isTrue()

    val configurable = PyWorkspaceStructureConfigurable(project)
    withContext(Dispatchers.EDT) {
      val page = configurable.createComponent()
      page.findAutoConfigureCheckBox().isSelected = false
      // The OK sequence: `apply` and `disposeUIResources` in one EDT event, so nothing the former
      // schedules off-EDT can reach the workspace model before the latter cancels it.
      configurable.apply()
      configurable.disposeUIResources()
    }

    assertThat(pyProjectModuleNames(project))
      .describedAs("No pyproject-generated module may survive OK on the turned-off toggle")
      .isEmpty()
    assertThat(moduleNames(project))
      .describedAs("Cleanup keeps the demoted root module and touches nothing else")
      .containsExactlyInAnyOrderElementsOf(baseline + "root")
  }

  private fun moduleNames(project: Project): List<String> =
    ModuleManager.getInstance(project).modules.map(Module::getName)

  private fun pyProjectModuleNames(project: Project): List<String> =
    ModuleManager.getInstance(project).modules.filter { it.isPyProjectTomlBased }.map(Module::getName)

  private fun VirtualFile.writePyProjectToml(projectName: String) {
    createChildData(this@PyWorkspaceStructureToggleOffTest, PY_PROJECT_TOML)
      .writeText("[project]\nname = \"$projectName\"\n")
  }

  /** The page holds exactly one checkbox, the auto-configure toggle; anything else is a changed layout. */
  private fun JComponent.findAutoConfigureCheckBox(): JCheckBox {
    val found = mutableListOf<JCheckBox>()
    fun walk(container: Container) {
      for (child in container.components) {
        if (child is JCheckBox) found += child
        if (child is Container) walk(child)
      }
    }
    walk(this)
    assertThat(found)
      .describedAs("Workspace Structure page must show the auto-configure checkbox and no other")
      .hasSize(1)
    return found.single()
  }
}
