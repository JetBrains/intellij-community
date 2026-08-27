// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.pipenv

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.io.toNioPathOrNull
import com.jetbrains.python.sdk.PythonInterpreterPresentationProvider
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor
import com.jetbrains.python.sdk.pySdkAdditionalData

/**
 * Labels a pipenv interpreter that lives outside the project.
 *
 * Pipenv keeps its one environment either in the project, as `.venv`, or under `$WORKON_HOME`. The default short label
 * is the last folder of the interpreter path. In the project that folder is `.venv`, which reads well, so this provider
 * keeps the default there. Outside it the folder is the project name and a hash of the project path, as in
 * `myproject-AbCdEf12`. The hash reads as noise, and it carries no version, so such an environment is labelled by its
 * Python version instead. The path stays readable wherever there is room for it.
 *
 * The same treatment poetry gets, for the same reason. Pipenv needs it more, because its folder name has no `-py3.12`
 * suffix to fall back on.
 */
internal class PipenvInterpreterPresentationProvider : PythonInterpreterPresentationProvider {
  override val flavorType: Class<out PythonSdkFlavor<*>> = PyPipEnvSdkFlavor::class.java

  /** The interpreter's own version string, which already reads as `Python 3.12.1`. */
  override fun getShortName(sdk: Sdk): String? =
    if (isEnvironmentOutsideProject(sdk)) sdk.versionString else null

  /**
   * True when the interpreter of [sdk] sits outside the project that uses it.
   *
   * Both sides are known without a file read. The project is the working directory that pipenv recorded when it made the
   * SDK. An SDK that recorded none keeps the default label, because there is nothing to compare its path against.
   */
  private fun isEnvironmentOutsideProject(sdk: Sdk): Boolean {
    val data = sdk.pySdkAdditionalData
    if (!data.hasValidWorkingDirectory()) return false
    val binary = sdk.homePath?.toNioPathOrNull() ?: return false
    return !binary.startsWith(data.workingDirectory)
  }
}
