// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.uv.sdk.configuration

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.PyBundle
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.errorProcessing.ErrorSink
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.errorProcessing.withProject
import com.jetbrains.python.impl.getSdkAssociatedModule
import com.jetbrains.python.onSuccess
import com.intellij.python.venv.environment.VenvEnvironment
import com.jetbrains.python.sdk.baseDir
import com.jetbrains.python.sdk.configuration.EnvCheckerResult
import com.jetbrains.python.sdk.configuration.findEnvOrNull
import com.jetbrains.python.sdk.configuration.findPythonVirtualEnvironments
import com.intellij.python.sdk.backend.detectPythonEnvironment
import com.jetbrains.python.sdk.setAssociationToModule
import com.intellij.platform.eel.provider.localEel
import com.intellij.python.pytools.resolveExecutable
import com.intellij.python.uv.backend.UvPyTool
import com.intellij.python.pyproject.model.internal.workspaceBridge.getToolWorkspaceLayout
import com.intellij.python.uv.common.UV_TOOL_ID
import com.jetbrains.python.sdk.add.v2.EelFileSystem
import com.jetbrains.python.sdk.uv.setupExistingEnvAndSdk
import com.jetbrains.python.sdk.uv.setupNewUvSdkAndEnv
import com.jetbrains.python.venvReader.tryResolvePath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path

private val logger = fileLogger()

internal suspend fun checkManageableUvEnvBase(
  module: Module,
  venvsInModule: List<PythonBinary>,
): EnvCheckerResult {
  UvPyTool.getInstance().resolveExecutable(EelFileSystem(localEel))?.path ?: return EnvCheckerResult.CannotConfigure
  val target = uvEnvTarget(module, venvsInModule)
  tryResolvePath(target.module.baseDir?.path) ?: return EnvCheckerResult.CannotConfigure
  val intentionName = PyBundle.message("sdk.set.up.uv.environment")
  val envFound = target.existing?.findEnvOrNull(intentionName)
  return envFound ?: EnvCheckerResult.EnvNotFound(intentionName)
}

internal suspend fun createUvSdk(module: Module, venvsInModule: List<PythonBinary>, envExists: Boolean): PyResult<Sdk> {
  val uv = UvPyTool.getInstance().resolveExecutable(EelFileSystem(localEel))?.path
           ?: return PyResult.localizedError(PyBundle.message("sdk.cannot.find.uv.executable"))
  val target = uvEnvTarget(module, venvsInModule)
  val sdkAssociatedModule = target.module
  val workingDir: Path? = tryResolvePath(sdkAssociatedModule.baseDir?.path)
  if (workingDir == null) {
    throw IllegalStateException("Can't determine working dir for the module")
  }

  val errorSink = ErrorSink().withProject(sdkAssociatedModule.project)
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
      it.setAssociationToModule(sdkAssociatedModule)
    }
  }
  return sdkSetupResult
}

/** The module a uv environment belongs to, and the uv environment that is already there. */
private class UvEnvTarget(val module: Module, val existing: PythonBinary?)

/**
 * Where the uv environment for [module] belongs: the root of the uv workspace it takes part in, or [module] itself.
 *
 * The workspace is asked about with [UV_TOOL_ID], never with the configurator's own tool id. uv declares one
 * workspace, and `uvBase` is a second entry point into the same tool rather than a second tool. Asked with `uvBase`
 * the question never matched, so a workspace member built its environment in its own directory: `uv venv` obeyed that
 * directory, `uv sync` then hoisted to the workspace root, and one workspace ended with two environments (PY-92193).
 *
 * [venvsInModule] holds the environments of [module] alone, which is the wrong directory for a member. So a member
 * reads the workspace root's directory instead, and an environment that is already there is adopted rather than
 * rebuilt over.
 */
private suspend fun uvEnvTarget(module: Module, venvsInModule: List<PythonBinary>): UvEnvTarget {
  val workspaceModule = module.getSdkAssociatedModule(UV_TOOL_ID)
  val venvs = if (workspaceModule == module) venvsInModule else workspaceModule.findPythonVirtualEnvironments()
  return UvEnvTarget(workspaceModule, venvs.firstOrNull { it.isUvEnv() })
}

/**
 * Whether [module] takes part in a uv workspace, as its root or as a member.
 *
 * A uv workspace declares one environment, at its root. An environment another tool makes for a member sits beside
 * it, and uv ignores it, so uv owns the setup of every module of a workspace. See
 * `PyProjectSdkConfigurationExtension.isExclusiveFor`.
 */
internal suspend fun uvOwnsSetupOf(module: Module): Boolean =
  readAction { module.getToolWorkspaceLayout(UV_TOOL_ID) } != null

internal fun PythonBinary.isUvEnv(): Boolean {
  return detectPythonEnvironment().successOrNull?.let { it is VenvEnvironment && "uv" in it.config } == true
}
