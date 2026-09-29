// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.uv.sdk.configuration

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.fileLogger
import com.jetbrains.python.PyBundle
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.errorProcessing.ErrorSink
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.errorProcessing.withProject
import com.jetbrains.python.impl.getSdkAssociatedPyProject
import com.jetbrains.python.onSuccess
import com.intellij.python.venv.environment.VenvEnvironment
import com.jetbrains.python.project.PyProject
import com.jetbrains.python.project.getEel
import com.jetbrains.python.project.project
import com.jetbrains.python.sdk.configuration.EnvCheckerResult
import com.jetbrains.python.sdk.configuration.findEnvOrNull
import com.jetbrains.python.sdk.configuration.findPythonVirtualEnvironments
import com.intellij.python.sdk.backend.detectPythonEnvironment
import com.jetbrains.python.sdk.setAssociationToModule
import com.intellij.python.pytools.resolveExecutable
import com.intellij.python.uv.backend.UvPyTool
import com.intellij.python.pyproject.model.internal.workspaceBridge.getToolWorkspaceLayout
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.python.sdk.backend.getSdkAPI
import com.intellij.python.uv.common.UV_TOOL_ID
import com.jetbrains.python.sdk.add.v2.EelFileSystem
import com.jetbrains.python.sdk.uv.setupExistingEnvAndSdk
import com.jetbrains.python.sdk.uv.setupNewUvSdkAndEnv
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val logger = fileLogger()

internal suspend fun checkManageableUvEnvBase(
  pyProject: PyProject,
  venvs: List<PythonBinary>,
): EnvCheckerResult {
  UvPyTool.getInstance().resolveExecutable(EelFileSystem(pyProject.getEel()))?.path ?: return EnvCheckerResult.CannotConfigure
  val target = uvEnvTarget(pyProject, venvs) ?: return EnvCheckerResult.CannotConfigure
  val intentionName = PyBundle.message("sdk.set.up.uv.environment")
  val envFound = target.existing?.findEnvOrNull(intentionName)
  return envFound ?: EnvCheckerResult.EnvNotFound(intentionName)
}

internal suspend fun createUvSdk(pyProject: PyProject, venvs: List<PythonBinary>, envExists: Boolean): PyResult<PythonInterpreter> {
  val uv = UvPyTool.getInstance().resolveExecutable(EelFileSystem(pyProject.getEel()))?.path
           ?: return PyResult.localizedError(PyBundle.message("sdk.cannot.find.uv.executable"))
  val target = uvEnvTarget(pyProject, venvs)
               ?: return PyResult.localizedError(PyBundle.message("sdk.cannot.find.uv.workspace.root"))
  val sdkAssociatedProject = target.pyProject
  val workingDir = sdkAssociatedProject.baseDir

  val errorSink = ErrorSink().withProject(sdkAssociatedProject.project)
  val sdkSetupResult = if (envExists) {
    target.existing?.let {
      setupExistingEnvAndSdk(it, uv, workingDir, false)
    } ?: run {
      logger.warn("Can't find existing uv environment in project, but it was expected. " +
                  "Probably it was deleted. New environment will be created")
      setupNewUvSdkAndEnv(uv, workingDir, null, errorSink)
    }
  }
  else setupNewUvSdkAndEnv(uv, workingDir, null, errorSink)

  sdkSetupResult.onSuccess {
    withContext(Dispatchers.EDT) {
      it.getSdkAPI().setAssociationToModule(sdkAssociatedProject.residesOnModule)
    }
  }
  return sdkSetupResult
}

/** The project a uv environment belongs to, and the uv environment that is already there. */
private class UvEnvTarget(val pyProject: PyProject, val existing: PythonBinary?)

/**
 * Where the uv environment for [pyProject] belongs: the root of the uv workspace it takes part in, or [pyProject]
 * itself.
 *
 * The workspace is asked about with [UV_TOOL_ID], never with the configurator's own tool id. uv declares one
 * workspace, and `uvBase` is a second entry point into the same tool rather than a second tool. Asked with `uvBase`
 * the question never matched, so a workspace member built its environment in its own directory: `uv venv` obeyed that
 * directory, `uv sync` then hoisted to the workspace root, and one workspace ended with two environments (PY-92193).
 *
 * [venvs] holds the environments of [pyProject]'s own directory, which is the wrong directory for a member. So a
 * member reads the workspace root's directory instead, and an environment that is already there is adopted rather
 * than rebuilt over.
 *
 * `null` when the workspace root is no Python project, and its directory is therefore unknown. See
 * [getSdkAssociatedPyProject].
 */
private suspend fun uvEnvTarget(pyProject: PyProject, venvs: List<PythonBinary>): UvEnvTarget? {
  val workspaceProject = pyProject.getSdkAssociatedPyProject(UV_TOOL_ID) ?: return null
  val found = if (workspaceProject == pyProject) venvs else workspaceProject.findPythonVirtualEnvironments()
  return UvEnvTarget(workspaceProject, found.firstOrNull { it.isUvEnv() })
}

/**
 * Whether [pyProject] takes part in a uv workspace, as its root or as a member.
 *
 * A uv workspace declares one environment, at its root. An environment another tool makes for a member sits beside
 * it, and uv ignores it, so uv owns the setup of every project of a workspace. See
 * `PyProjectSdkConfigurationExtension.isExclusiveFor`.
 */
internal suspend fun uvOwnsSetupOf(pyProject: PyProject): Boolean =
  readAction { pyProject.residesOnModule.getToolWorkspaceLayout(UV_TOOL_ID) } != null

internal fun PythonBinary.isUvEnv(): Boolean {
  return detectPythonEnvironment().successOrNull?.let { it is VenvEnvironment && "uv" in it.config } == true
}
