// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.poetry

import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.NlsSafe
import com.intellij.python.pyproject.PyProjectToml
import com.intellij.util.PathUtil
import com.jetbrains.python.PyBundle
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.PythonModuleTypeBase
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.icons.PythonIcons
import com.jetbrains.python.packaging.common.PythonOutdatedPackage
import com.jetbrains.python.resolvePythonBinary
import com.jetbrains.python.sdk.PythonSdkUtil
import com.jetbrains.python.sdk.createSdk
import com.jetbrains.python.sdk.getOrCreateAdditionalData
import org.jetbrains.annotations.ApiStatus.Internal
import java.nio.file.Path
import java.util.regex.Pattern
import javax.swing.Icon
import kotlin.io.path.pathString

// TODO: Provide a special icon for poetry
val POETRY_ICON: Icon = PythonIcons.Python.Origami

@Internal
fun suggestedSdkName(basePath: Path): @NlsSafe String = "Poetry (${PathUtil.getFileName(basePath.pathString)})"


@Internal
suspend fun createNewPoetrySdk(
  moduleBasePath: Path,
  existingSdks: List<Sdk>,
  basePythonBinaryPath: PythonBinary?,
  installPackages: Boolean,
): PyResult<Sdk> {
  val pythonBinaryPath = setUpPoetry(moduleBasePath, basePythonBinaryPath, installPackages).getOr { return it }

  return createPoetrySdk(moduleBasePath, existingSdks, pythonBinaryPath)
}

@Internal
suspend fun createPoetrySdk(
  moduleBasePath: Path,
  existingSdks: List<Sdk>,
  pythonBinaryPath: PythonBinary,
): PyResult<Sdk> = createSdk(
  pythonBinaryPath = pythonBinaryPath,
  existingSdks = existingSdks,
  associatedProjectPath = moduleBasePath.toString(),
  suggestedSdkName = suggestedSdkName(moduleBasePath),
  sdkAdditionalData = PyPoetrySdkAdditionalData(moduleBasePath)
)

internal val Sdk.isPoetry: Boolean
  get() {
    if (!PythonSdkUtil.isPythonSdk(this)) {
      return false
    }

    return getOrCreateAdditionalData() is PyPoetrySdkAdditionalData
  }

internal fun sdkHomes(sdks: List<Sdk>): Set<String> {
  return sdks.mapNotNull { it.homePath }.toSet()
}

internal fun allModules(project: Project?): List<Module> {
  return project?.let {
    ModuleUtil.getModulesOfType(it, PythonModuleTypeBase.getInstance())
  }?.sortedBy { it.name } ?: emptyList()
}

private suspend fun setUpPoetry(moduleBasePath: Path, basePythonBinaryPath: PythonBinary?, installPackages: Boolean): PyResult<PythonBinary> {
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