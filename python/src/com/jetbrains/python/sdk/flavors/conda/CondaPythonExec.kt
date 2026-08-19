// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.flavors.conda

import com.intellij.execution.Platform
import com.intellij.execution.target.TargetedCommandLineBuilder
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.registry.Registry
import com.jetbrains.python.packaging.getCondaBasePython
import com.jetbrains.python.sdk.activationEnvironment
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import kotlin.io.path.isExecutable


/**
 * Use ``python`` instead of ``conda run`` for local interpreters only (remote are unaffected)
 */
val usePythonForLocalConda: Boolean get() = Registry.`is`("use.python.for.local.conda")

/**
 * Adds python from conda SDK to [targetedCommandLineBuilder].
 * Encapsulates logic on choosing between legacy (read env + python) and new (conda run) approaches.
 * For the legacy it either takes homePath from [sdk] or base conda but from the base env only.
 * So, you should provide sdk or (if you do not have it) base conda env. Otherwise, fallbacks to "conda run" even in legacy mode
 */

@ApiStatus.Internal
fun addCondaPythonToTargetCommandLine(targetedCommandLineBuilder: TargetedCommandLineBuilder,
                                      condaEnv: PyCondaEnv,
                                      sdk: Sdk?) {
  val legacyUsed = usePythonForLocalConda
                   && targetedCommandLineBuilder.request.configuration == null
                   && configureLegacy(sdk, condaEnv, targetedCommandLineBuilder)
  if (!legacyUsed) {
    // Conda run
    condaEnv.addCondaToTargetBuilder(targetedCommandLineBuilder)
    targetedCommandLineBuilder.addParameter("python")
  }
  targetedCommandLineBuilder.applyCondaActivationEnv(sdk, condaEnv)
}

/**
 * Applies the conda environment's activation environment to this builder through the regular activation pipeline.
 * Local Windows only (conda's DLL/PATH bugs are Windows-specific and activation reads the local shell): with an
 * [sdk] the target env is activated, otherwise the base conda env (used e.g. by the package cache). The base
 * install's `Library\bin` is contributed by the conda activation post-processor.
 */
private fun TargetedCommandLineBuilder.applyCondaActivationEnv(sdk: Sdk?, condaEnv: PyCondaEnv) {
  if (request.targetPlatform.platform != Platform.WINDOWS || request.configuration != null) return
  val activationEnv = runBlockingMaybeCancellable {
    if (sdk != null) {
      sdk.activationEnvironment()
    }
    else {
      val baseCondaPython = getCondaBasePython(condaEnv.fullCondaPathOnTarget) ?: return@runBlockingMaybeCancellable null
      Path.of(baseCondaPython).activationEnvironment()
    }
  }
  for ((name, value) in activationEnv?.successOrNull ?: emptyMap()) {
    addEnvironmentVariable(name, value)
  }
}

private fun configureLegacy(sdk: Sdk?,
                            condaEnv: PyCondaEnv,
                            targetedCommandLineBuilder: TargetedCommandLineBuilder): Boolean {
  val logger = Logger.getInstance("CondaPythonLegacy")
  var pythonPath = sdk?.homePath
  val envIdentity = condaEnv.envIdentity
  if (pythonPath == null && envIdentity is PyCondaEnvIdentity.UnnamedEnv && envIdentity.isBase) {
    pythonPath = getCondaBasePython(condaEnv.fullCondaPathOnTarget)
  }
  if (pythonPath.isNullOrBlank() || !Path.of(pythonPath).isExecutable()) {
    logger.warn("Can't find python path to use, will use conda run instead")
    return false
  }
  targetedCommandLineBuilder.setExePath(pythonPath)
  return true
}