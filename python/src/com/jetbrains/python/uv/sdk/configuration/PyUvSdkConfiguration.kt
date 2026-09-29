// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.uv.sdk.configuration

import com.intellij.python.community.common.tools.ToolId
import com.intellij.python.pyproject.PY_PROJECT_TOML
import com.intellij.python.pytools.backend.PyExecutableCache
import com.intellij.python.uv.backend.UvPyTool
import com.intellij.python.uv.common.UV_TOOL_ID
import com.jetbrains.python.PyBundle
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.project.PyProject
import com.jetbrains.python.project.eelDescriptor
import com.jetbrains.python.sdk.configuration.CreateInterpreterInfo
import com.jetbrains.python.sdk.configuration.EnvCheckerResult
import com.jetbrains.python.sdk.configuration.PyProjectTomlConfigurationExtension
import com.jetbrains.python.sdk.configuration.prepareSdkCreator
import com.jetbrains.python.uv.findUvLock
import java.nio.file.Path

internal class PyUvSdkConfiguration : PyProjectTomlConfigurationExtension {
  override val toolId: ToolId = UV_TOOL_ID
  override val potentialDependencyFiles: Set<String> = setOf(PY_PROJECT_TOML)

  override suspend fun isExclusiveFor(pyProject: PyProject): Boolean = uvOwnsSetupOf(pyProject)

  override suspend fun checkEnvironmentAndPrepareSdkCreator(pyProject: PyProject, venvs: List<PythonBinary>): CreateInterpreterInfo? =
    prepareSdkCreator(
      { checkManageableUvEnvWithUvLock(pyProject, venvs, tomlCheckedByWorkspaceTools = false) }
    ) { envExists -> { createUvSdk(pyProject, venvs, envExists) } }

  override suspend fun createSdkWithoutPyProjectTomlChecks(pyProject: PyProject, venvs: List<PythonBinary>): CreateInterpreterInfo? =
    prepareSdkCreator(
      { checkManageableUvEnvWithUvLock(pyProject, venvs, tomlCheckedByWorkspaceTools = true) }
    ) { envExists -> { createUvSdk(pyProject, venvs, envExists) } }

  private suspend fun checkManageableUvEnvWithUvLock(
    pyProject: PyProject,
    venvs: List<PythonBinary>,
    tomlCheckedByWorkspaceTools: Boolean
  ): EnvCheckerResult {
    val baseCheckResult = checkManageableUvEnvBase(pyProject, venvs)
    return when (baseCheckResult) {
      is EnvCheckerResult.EnvFound, is EnvCheckerResult.SuggestToolInstallation -> baseCheckResult
      is EnvCheckerResult.EnvNotFound -> if (tomlCheckedByWorkspaceTools || declaresUv(pyProject)) baseCheckResult else EnvCheckerResult.CannotConfigure
      is EnvCheckerResult.CannotConfigure -> if (declaresUv(pyProject)) {
        // uv was just installed; drop the detection cache so the next lookup finds it (don't persist).
        val pathPersister: (Path) -> Unit = { _ ->
          PyExecutableCache.getInstance().invalidate(pyProject.eelDescriptor, UvPyTool.getInstance())
        }
        val toolName = "uv"
        EnvCheckerResult.SuggestToolInstallation(
          toolToInstall = toolName,
          pathPersister = pathPersister,
          intentionName = PyBundle.message("sdk.create.custom.venv.install.fix.title", toolName)
        )
      } else baseCheckResult
    }
  }

  /**
   * Whether the files alone say this is a uv project, with no uv installed to ask.
   *
   * A `uv.lock` says so. So does a declared uv workspace: `[tool.uv.workspace]` names the member, and uv manages it
   * whether or not the workspace has been synchronized yet. Read from the lock alone, a workspace nobody had synced
   * offered no uv option at all, so on a machine without uv another tool's option was offered for it instead.
   */
  private suspend fun declaresUv(pyProject: PyProject): Boolean = findUvLock(pyProject) != null || uvOwnsSetupOf(pyProject)

  override fun asPyProjectTomlSdkConfigurationExtension(): PyProjectTomlConfigurationExtension = this
}
