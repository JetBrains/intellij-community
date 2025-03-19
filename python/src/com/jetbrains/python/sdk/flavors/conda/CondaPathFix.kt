// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.flavors.conda

import com.intellij.execution.Platform
import com.intellij.execution.target.FullPathOnTarget
import com.intellij.execution.target.TargetedCommandLineBuilder
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.util.EnvReader
import com.jetbrains.python.sdk.PySdkUtil
import com.jetbrains.python.sdk.getOrCreateAdditionalData
import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.isExecutable

private val LOG = Logger.getInstance("CondaLogger")
/**
 * Workaround for cases like ``https://github.com/conda/conda/issues/11795`` and warnings (see [addEnvVars])
 *
 * It reads envs vars out of "activate.bat" on Windows.
 * Something unreliable and redundant, but still needed due to conda bugs.
 *
 * Non-conda, non-local and non-windows command lines are silently ignored
 * */

fun TargetedCommandLineBuilder.fixCondaPathEnvIfNeeded(sdk: Sdk) {
  if (!localOnWindows) return
  val condaData = (sdk.getOrCreateAdditionalData().flavorAndData.data as? PyCondaFlavorData) ?: return
  val pythonHomePath = sdk.homePath
  if (pythonHomePath == null) {
    LOG.warn("No home path for $this, will skip 'venv activation'")
    return
  }
  addEnvVars(PySdkUtil.activateVirtualEnv(sdk), condaData.env.fullCondaPathOnTarget)
}

fun TargetedCommandLineBuilder.fixCondaPathEnvIfNeeded(condaPathOnTarget: FullPathOnTarget) {
  if (!localOnWindows) return
  val condaPath = Path.of(condaPathOnTarget)
  if (!condaPath.exists()) {
    LOG.warn("$condaPath doesn't exist")
    return
  }
  val activateBat = condaPath.resolveSibling("activate.bat")
  if (!activateBat.isExecutable()) {
    LOG.warn("$activateBat doesn't exist or can't be read")
    return
  }
  try {
    val envs = EnvReader().readBatEnv(activateBat, emptyList())
    addEnvVars(envs, condaPathOnTarget)
  }
  catch (e: IOException) {
    LOG.warn("Can't read env vars", e)
  }
}

/**
 * Bugs hit only Windows conda and env activation only works on local conda
 */
private val TargetedCommandLineBuilder.localOnWindows: Boolean
  get() = request.let {
    it.targetPlatform.platform == Platform.WINDOWS && it.configuration == null
  }

/**
 * Special fix for conda PATH.
 * conda itself runs base python under the hood. When run non-base env, only non-base env gets activated, so conda
 * first runs non-activated base python. It loads ``sitecustomize.py`` and it leads to mkl loading that throws warning
 * due to path issue (DLL not found).
 * We add this folder to conda path
 */
private fun TargetedCommandLineBuilder.addEnvVars(envs: Map<String, String>, condaPathOnTarget: FullPathOnTarget) {
  val extraPath = Path.of(condaPathOnTarget).parent?.parent?.resolve("Library")?.resolve("Bin")
  if (extraPath == null || !extraPath.exists() || !extraPath.isDirectory()) {
    LOG.warn("$extraPath doesn't exist")
  }
  LOG.info("Patching envs")
  for ((k, v) in envs) {
    val fixedVal = if (k.equals("Path", ignoreCase = true) && extraPath != null) {
      v + request.targetPlatform.platform.pathSeparator + extraPath
    }
    else v
    addEnvironmentVariable(k, fixedVal)
  }
}