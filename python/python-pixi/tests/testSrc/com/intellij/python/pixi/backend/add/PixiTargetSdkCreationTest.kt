// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pixi.backend.add

import com.intellij.execution.target.TargetEnvironmentConfiguration
import com.intellij.python.junit5Tests.framework.pyProjectFixture
import com.intellij.python.pixi.backend.workspace.PixiWorkspace
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.sdk.ModuleOrProject
import com.jetbrains.python.sdk.PythonSdkAdditionalData
import com.jetbrains.python.sdk.add.v2.FileSystem
import com.jetbrains.python.sdk.add.v2.FileSystemFactory
import com.jetbrains.python.sdk.add.v2.PathHolder
import com.jetbrains.python.sdk.add.v2.PythonLocalAddInterpreterModel
import com.jetbrains.python.newProjectWizard.projectPath.ProjectPathFlows
import com.jetbrains.python.target.ui.TargetPanelExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.io.path.writeText

@TestApplication
@Subsystems.Interpreters
@Layers.Functional
internal class PixiTargetSdkCreationTest {
  private val rootFixture = tempPathFixture()
  private val projectFixture = projectFixture(rootFixture)
  private val pyProjectFixture = projectFixture.pyProjectFixture(rootFixture)

  @Test
  fun `a target interpreter uses target SDK setup when the Python project exists`(): Unit = timeoutRunBlocking {
    val root = rootFixture.get()
    root.resolve("pixi.toml").writeText("[workspace]\n[environments]\ndocs = []\n")
    val pyProject = pyProjectFixture.get()
    val target = object : TargetEnvironmentConfiguration("pixi-test") {
      override var projectRootOnTarget: String = "/remote project"
    }
    var selectedBinary: PathHolder.Target? = null
    var selectedData: PythonSdkAdditionalData? = null

    val fileSystem = object : FileSystem<PathHolder.Target> by FileSystemFactory.getInstance().create(target) {
      override suspend fun resolveInWorkingDir(workingDir: Path, dirName: String): PathHolder.Target =
        error("Target resolution must use the workspace mirror.")

      override fun resolvePythonBinary(pythonHome: PathHolder.Target): PathHolder.Target =
        PathHolder.Target("${pythonHome.pathString}/bin/python")

      override suspend fun setupSdk(
        moduleOrProject: ModuleOrProject,
        pythonBinaryPath: PathHolder.Target,
        sdkAdditionalData: PythonSdkAdditionalData,
        targetPanelExtension: TargetPanelExtension?,
        suggestedSdkName: String?,
      ): PyResult<PythonInterpreter> {
        selectedBinary = pythonBinaryPath
        selectedData = sdkAdditionalData
        return PyResult.localizedError("The target SDK setup was called.")
      }
    }
    val model = PythonLocalAddInterpreterModel(ProjectPathFlows.create(root), fileSystem)
    val form = PixiEnvironmentForm(model, existingOnly = true, workspacePathResolver = { executable, workspaceRoot, relativePath ->
      assertThat(executable).isEqualTo(PathHolder.Target("/remote/pixi"))
      assertThat(workspaceRoot).isEqualTo(root)
      PyResult.success(PathHolder.Target("/remote project/$relativePath"))
    })

    form.createSdk(ModuleOrProject.ModuleAndProject(pyProject), PixiSelection(
      PathHolder.Target("/remote/pixi"), PixiWorkspace(root, listOf("default", "docs")), "docs", initializeWorkspace = false,
    ))

    assertThat(selectedBinary).isEqualTo(PathHolder.Target("/remote project/.pixi/envs/docs/bin/python"))
    assertThat(selectedData?.requirementsPath).isEqualTo(root.resolve("pixi.toml"))
    assertThat(selectedData?.workingDirectory).isEqualTo(root)
  }
}
