// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.poetry

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.NlsSafe
import com.intellij.python.pyproject.PyProjectToml
import com.intellij.util.PathUtil
import com.jetbrains.python.PyBundle
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.packaging.common.PythonOutdatedPackage
import com.jetbrains.python.sdk.add.v2.PathHolder
import com.jetbrains.python.sdk.createSdk
import com.jetbrains.python.sdk.getOrCreateAdditionalData
import com.jetbrains.python.sdk.impl.resolvePythonBinary
import com.jetbrains.python.sdk.legacy.PythonSdkUtil
import org.jetbrains.annotations.ApiStatus.Internal
import java.nio.file.Path
import java.util.regex.Pattern
import kotlin.io.path.pathString


@Internal
fun suggestedSdkName(basePath: Path): @NlsSafe String = "Poetry (${PathUtil.getFileName(basePath.pathString)})"


@Internal
suspend fun createNewPoetrySdk(
  moduleBasePath: Path,
  basePythonBinaryPath: PythonBinary,
  installPackages: Boolean,
): PyResult<Sdk> {
  val pythonBinaryPath = setUpPoetry(moduleBasePath, basePythonBinaryPath, installPackages).getOr { return it }

  return createPoetrySdk(
    basePath = moduleBasePath,
    pythonBinaryPath = PathHolder.Eel(pythonBinaryPath)
  )
}

@Internal
suspend fun createPoetrySdk(
  basePath: Path,
  pythonBinaryPath: PathHolder.Eel,
): PyResult<Sdk> = createSdk(
  pythonBinaryPath = pythonBinaryPath,
  associatedModulePath = basePath.toString(),
  suggestedSdkName = suggestedSdkName(basePath),
  sdkAdditionalData = PyPoetrySdkAdditionalData(basePath)
)

internal val Sdk.isPoetry: Boolean
  get() {
    if (!PythonSdkUtil.isPythonSdk(this)) {
      return false
    }

    return getOrCreateAdditionalData() is PyPoetrySdkAdditionalData
  }

private suspend fun setUpPoetry(moduleBasePath: Path, basePythonBinaryPath: PythonBinary, installPackages: Boolean): PyResult<PythonBinary> {
  val init = PyProjectToml.findInRoot(moduleBasePath) == null
  val pythonHomePath = setupPoetry(moduleBasePath, basePythonBinaryPath, installPackages, init).getOr { return it }
  val pythonBinaryPath = pythonHomePath.resolvePythonBinary()
                         ?: return PyResult.localizedError(PyBundle.message("python.sdk.cannot.setup.sdk", pythonHomePath))
  return PyResult.success(pythonBinaryPath)
}

fun parsePoetryShowOutdated(input: String): Map<String, PythonOutdatedPackage> {
  return input
    .lines()
    .map { it.trim() }
    .filter { it.isNotBlank() }
    .mapNotNull { line ->
      line.split(Pattern.compile(" +"))
        .takeIf { it.size > 3 }?.let { it[0] to PythonOutdatedPackage(it[0], it[1], it[2]) }
    }.toMap()
}