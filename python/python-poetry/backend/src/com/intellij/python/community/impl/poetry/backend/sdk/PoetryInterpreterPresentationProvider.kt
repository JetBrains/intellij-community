// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.impl.poetry.backend.sdk

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.io.toNioPathOrNull
import com.jetbrains.python.sdk.PythonInterpreterPresentationProvider
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor
import com.jetbrains.python.sdk.poetry.PyPoetrySdkFlavor
import com.jetbrains.python.sdk.pySdkAdditionalData

/**
 * Labels a poetry interpreter that lives outside the project.
 *
 * Poetry keeps an environment either in the project, as `.venv`, or in its own cache directory. The default short label
 * is the last folder of the interpreter path. In the project that folder is `.venv`, which reads well, so this provider
 * keeps the default there. In the cache the folder carries the project name, a hash, and the version, as in
 * `myproject-AbCdEf12-py3.12`. The hash reads as noise, and the whole name is too long for a status bar. So a cache
 * environment is labelled by its Python version instead. The path stays readable wherever there is room for it.
 */
internal class PoetryInterpreterPresentationProvider : PythonInterpreterPresentationProvider {
  override val flavorType: Class<out PythonSdkFlavor<*>> = PyPoetrySdkFlavor::class.java

  /** The interpreter's own version string, which already reads as `Python 3.12.1`. */
  override fun getShortName(sdk: Sdk): String? =
    if (isEnvironmentOutsideProject(sdk)) sdk.versionString else null

  /**
   * True when the interpreter of [sdk] sits outside the project that uses it.
   *
   * Both sides are known without a file read. The project is the working directory that poetry recorded when it made the
   * SDK. An SDK that recorded none keeps the default label, because there is nothing to compare its path against.
   */
  private fun isEnvironmentOutsideProject(sdk: Sdk): Boolean {
    val data = sdk.pySdkAdditionalData
    if (!data.hasValidWorkingDirectory()) return false
    val binary = sdk.homePath?.toNioPathOrNull() ?: return false
    return !binary.startsWith(data.workingDirectory)
  }
}
