// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.poetry

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.util.progress.withProgressText
import com.intellij.python.pyproject.PyProjectToml
import com.intellij.util.PathUtil
import com.jetbrains.python.PyBundle
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.errorProcessing.ErrorSink
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.packaging.common.PythonOutdatedPackage
import com.jetbrains.python.sdk.add.v2.FileSystem
import com.jetbrains.python.sdk.add.v2.PathHolder
import com.jetbrains.python.sdk.add.v2.toEelFileSystem
import com.jetbrains.python.sdk.legacy.PythonSdkUtil
import com.jetbrains.python.sdk.pySdkAdditionalData
import com.jetbrains.python.target.ui.TargetPanelExtension
import java.nio.file.Path
import java.util.regex.Pattern
import kotlin.io.path.pathString


internal fun suggestedSdkName(basePath: Path): @NlsSafe String = "Poetry (${PathUtil.getFileName(basePath.pathString)})"


internal suspend fun createNewPoetrySdk(
  moduleBasePath: Path,
  basePythonBinaryPath: PythonBinary,
  installPackages: Boolean,
  errorSink: ErrorSink,
  inProjectEnv: Boolean = false,
): PyResult<Sdk> {
  val fileSystem = moduleBasePath.toEelFileSystem()
  return createNewPoetrySdk(
    moduleBasePath = moduleBasePath,
    basePythonBinaryPath = PathHolder.Eel(basePythonBinaryPath),
    fileSystem = fileSystem,
    poetryExecutable = null,
    installPackages = installPackages,
    errorSink = errorSink,
    inProjectEnv = inProjectEnv,
  )
}

internal suspend fun <P : PathHolder> createNewPoetrySdk(
  moduleBasePath: Path,
  basePythonBinaryPath: P,
  fileSystem: FileSystem<P>,
  poetryExecutable: P?,
  installPackages: Boolean,
  errorSink: ErrorSink,
  inProjectEnv: Boolean = false,
  targetPanelExtension: TargetPanelExtension? = null,
): PyResult<Sdk> {
  val pythonBinaryPath = setUpPoetry(
    moduleBasePath = moduleBasePath,
    basePythonBinaryPath = basePythonBinaryPath,
    fileSystem = fileSystem,
    poetryExecutable = poetryExecutable,
    installPackages = installPackages,
    errorSink = errorSink,
    inProjectEnv = inProjectEnv,
  ).getOr { return it }

  return createPoetrySdk(
    basePath = moduleBasePath,
    pythonBinaryPath = pythonBinaryPath,
    fileSystem = fileSystem,
    targetPanelExtension = targetPanelExtension,
  )
}

internal suspend fun <P : PathHolder> createPoetrySdk(
  basePath: Path,
  pythonBinaryPath: P,
  fileSystem: FileSystem<P>,
  targetPanelExtension: TargetPanelExtension? = null,
): PyResult<Sdk> = withProgressText(PyBundle.message("python.sdk.progress.poetry.configuring")) {
  fileSystem.setupSdk(
    project = null,
    pythonBinaryPath = pythonBinaryPath,
    sdkAdditionalData = PyPoetrySdkAdditionalData(basePath),
    targetPanelExtension = targetPanelExtension,
    suggestedSdkName = null,
  )
}

internal val Sdk.isPoetry: Boolean
  get() = PythonSdkUtil.isPythonSdk(this) && pySdkAdditionalData.flavor == PyPoetrySdkFlavor

private suspend fun <P : PathHolder> setUpPoetry(
  moduleBasePath: Path,
  basePythonBinaryPath: P,
  fileSystem: FileSystem<P>,
  poetryExecutable: P?,
  installPackages: Boolean,
  errorSink: ErrorSink,
  inProjectEnv: Boolean,
): PyResult<P> {
  val init = PyProjectToml.findInRoot(moduleBasePath) == null
  val pythonHomePath = setupPoetry(
    projectPath = moduleBasePath,
    fileSystem = fileSystem,
    poetryExecutable = poetryExecutable,
    basePythonBinaryPath = basePythonBinaryPath,
    installPackages = installPackages,
    init = init,
    errorSink = errorSink,
    inProjectEnv = inProjectEnv,
  ).getOr { return it }
  val pythonBinaryPath = fileSystem.resolvePythonBinary(pythonHomePath)
                         ?: return PyResult.localizedError(PyBundle.message("python.sdk.cannot.setup.sdk", pythonHomePath))
  return PyResult.success(pythonBinaryPath)
}

internal fun parsePoetryShowOutdated(input: String): Map<String, PythonOutdatedPackage> {
  return input
    .lines()
    .map { it.trim() }
    .filter { it.isNotBlank() }
    .mapNotNull { line ->
      line.split(Pattern.compile(" +"))
        .takeIf { it.size > 3 }?.let { it[0] to PythonOutdatedPackage(it[0], it[1], it[2]) }
    }.toMap()
}
