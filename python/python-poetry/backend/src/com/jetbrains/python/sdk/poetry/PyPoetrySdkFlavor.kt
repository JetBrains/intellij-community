// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.poetry

import com.intellij.python.sdk.backend.fileName
import com.intellij.python.sdk.backend.resolvePythonHome
import com.jetbrains.python.venvReader.VirtualEnvReader
import com.jetbrains.python.sdk.add.v2.PathHolder
import com.intellij.python.community.impl.poetry.backend.PoetryPyTool
import com.intellij.openapi.util.io.toNioPathOrNull
import com.intellij.python.community.impl.poetry.common.icons.PythonCommunityImplPoetryCommonIcons
import com.jetbrains.python.PyInternalExecApi
import com.jetbrains.python.sdk.PythonSdkAdditionalData
import com.jetbrains.python.sdk.flavors.CPythonSdkFlavor
import com.jetbrains.python.sdk.flavors.PyFlavorData
import com.jetbrains.python.sdk.flavors.PythonFlavorProvider
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import javax.swing.Icon


/**
 *  This source code is edited by @koxudaxi Koudai Aono <koxudaxi@gmail.com>
 */

@ApiStatus.Internal
@PyInternalExecApi
object PyPoetrySdkFlavor : CPythonSdkFlavor<PyFlavorData.Empty>() {
  override fun getIcon(): Icon = PythonCommunityImplPoetryCommonIcons.Poetry
  override fun getFlavorDataClass(): Class<PyFlavorData.Empty> = PyFlavorData.Empty::class.java
  override fun getManagerId(): String = PoetryPyTool.getInstance().fusId.value

  /** Poetry names an environment by where it is, see [poetryEnvRefOf]. */
  override fun toolEnvRefOf(pythonBinary: PathHolder, data: PyFlavorData.Empty): String? = poetryEnvRefOf(pythonBinary)

  override fun migrateAdditionalData(
    additionalData: PythonSdkAdditionalData,
    data: PyFlavorData.Empty,
  ): AdditionalDataMigration<PyFlavorData.Empty> {
    val workingDirectory = additionalData.associatedModulePath?.takeIf { it.isNotBlank() }?.toNioPathOrNull()
    return AdditionalDataMigration(data, workingDirectory)
  }

  override fun isValidSdkPath(pythonBinaryPath: Path): Boolean = false
}

/** The env ref of the project's own `.venv`. Any other env ref names a cache environment, see [poetryCacheEnvRef]. */
@ApiStatus.Internal
const val POETRY_IN_PROJECT_ENV_REF: String = "in-project"

/** The `-py<version>` suffix poetry gives the folder of a cache environment, such as `app-Xa2b3c4d-py3.13`. */
private val CACHE_ENV_VERSION: Regex = Regex("""-py(\d+\.\d+)$""")

/** The prefix of the env ref of a cache environment, as poetry writes it before the version in the folder name. */
private const val CACHE_ENV_REF_PREFIX = "py"

/**
 * The env ref of the poetry environment whose interpreter is [pythonBinary], local or on a target:
 * [POETRY_IN_PROJECT_ENV_REF] for a `.venv`, and [poetryCacheEnvRef] for a cache environment. A project has one cache
 * environment per Python version, and poetry puts that version at the end of the folder name. `null` for any other
 * environment, which is then named by its binary path.
 */
@ApiStatus.Internal
fun poetryEnvRefOf(pythonBinary: PathHolder): String? {
  val envRootName = pythonBinary.resolvePythonHome().fileName
  if (envRootName == VirtualEnvReader.DEFAULT_VIRTUALENV_DIRNAME) return POETRY_IN_PROJECT_ENV_REF
  return poetryCacheEnvVersion(envRootName)?.let(::poetryCacheEnvRef)
}

/** The env ref of the cache environment of Python [version], such as `py3.13`: the suffix of its folder name. */
@ApiStatus.Internal
fun poetryCacheEnvRef(version: String): String = CACHE_ENV_REF_PREFIX + version

/** The Python version of the cache environment that [envRef] names, see [poetryCacheEnvRef]. */
@ApiStatus.Internal
fun poetryCacheEnvRefVersion(envRef: String): String = envRef.removePrefix(CACHE_ENV_REF_PREFIX)

/** The Python version of the poetry cache environment whose folder is [envRootName], or null when it is not one. */
@ApiStatus.Internal
fun poetryCacheEnvVersion(envRootName: String): String? = CACHE_ENV_VERSION.find(envRootName)?.groupValues?.get(1)

internal class PyPoetrySdkFlavorProvider : PythonFlavorProvider {
  override fun getFlavor(): PythonSdkFlavor<*> = PyPoetrySdkFlavor
}