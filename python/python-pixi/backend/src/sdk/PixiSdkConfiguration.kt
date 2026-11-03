// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pixi.backend.sdk

import com.intellij.python.community.execService.ExecOptions
import com.intellij.python.community.execService.ZeroCodeStdoutTransformer
import com.intellij.python.pixi.backend.PIXI_TOOL_ID
import com.intellij.python.pixi.backend.PixiPyTool
import com.intellij.python.pixi.backend.PyPixiBackendBundle
import com.intellij.python.pixi.backend.workspace.DEFAULT_ENVIRONMENT
import com.intellij.python.pixi.backend.workspace.findPixiWorkspace
import com.intellij.python.pixi.backend.workspace.PixiWorkspaceOperations
import com.intellij.python.pyproject.PY_PROJECT_TOML
import com.intellij.python.pytools.backend.runtime.PyToolRuntime
import com.intellij.python.pytools.backend.runtime.executeAndHandleErrors
import com.intellij.python.sdk.backend.PythonInterpreterRegistry
import com.intellij.python.sdk.backend.detectPythonEnvironment
import com.intellij.python.sdk.backend.getPythonInfo
import com.intellij.python.sdk.backend.resolveExecutable
import com.intellij.python.sdk.common.PyEnvRef
import com.intellij.python.sdk.common.PyInterpreterRef
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.project.PyProject
import com.jetbrains.python.project.getEel
import com.jetbrains.python.project.project
import com.jetbrains.python.sdk.add.v2.toFileSystem
import com.jetbrains.python.sdk.configuration.CreateInterpreterInfo
import com.jetbrains.python.sdk.configuration.EnvCheckerResult
import com.jetbrains.python.sdk.configuration.PyProjectSdkConfigurationExtension
import com.jetbrains.python.sdk.configuration.PyProjectTomlConfigurationExtension
import com.jetbrains.python.sdk.configuration.prepareSdkCreator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class PixiSdkConfiguration : PyProjectSdkConfigurationExtension {
  override val toolId get() = PIXI_TOOL_ID
  override val potentialDependencyFiles: Set<String> = setOf("pixi.toml", PY_PROJECT_TOML)

  override suspend fun isExclusiveFor(pyProject: PyProject): Boolean = withContext(Dispatchers.IO) {
    findPixiWorkspace(pyProject.baseDir) != null
  }

  override suspend fun checkEnvironmentAndPrepareSdkCreator(pyProject: PyProject, venvs: List<PythonBinary>): CreateInterpreterInfo? {
    val workspace = withContext(Dispatchers.IO) { findPixiWorkspace(pyProject.baseDir) } ?: return null
    val fileSystem = pyProject.getEel().toFileSystem()
    val executable = fileSystem.resolveExecutable(PixiPyTool.getInstance()) ?: return null
    val home = fileSystem.resolveInWorkingDir(workspace.root, ".pixi/envs/$DEFAULT_ENVIRONMENT") ?: return null
    val binary = fileSystem.resolvePythonBinary(home)
    val pythonInfo = withContext(Dispatchers.IO) { binary?.path?.detectPythonEnvironment()?.successOrNull }?.getPythonInfo()?.successOrNull
    val intentionName = PyPixiBackendBundle.message("python.pixi.sdk.setup")
    return prepareSdkCreator(
      { pythonInfo?.let { EnvCheckerResult.EnvFound(it, intentionName) } ?: EnvCheckerResult.EnvNotFound(intentionName) },
    ) { envExists ->
      creator@{
        if (!envExists) {
          val runtime = PyToolRuntime(fileSystem.getBinaryToExec(executable, workspace.root), ExecOptions())
          PixiWorkspaceOperations.run(workspace.root) {
            runtime.executeAndHandleErrors(
              "install", "--environment=$DEFAULT_ENVIRONMENT", transformer = ZeroCodeStdoutTransformer,
            )
          }.getOr { return@creator it }
        }
        val ref = PyInterpreterRef.native(PixiPyTool.getInstance().fusId, PyEnvRef(DEFAULT_ENVIRONMENT))
        PythonInterpreterRegistry.getInstance(pyProject.project).newPythonInterpreter(pyProject, ref)
      }
    }
  }

  override fun asPyProjectTomlSdkConfigurationExtension(): PyProjectTomlConfigurationExtension? = null
}
