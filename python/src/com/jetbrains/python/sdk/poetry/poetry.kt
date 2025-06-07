// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.poetry

import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.util.PathUtil
import com.jetbrains.python.PyBundle
import com.jetbrains.python.PythonModuleTypeBase
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.icons.PythonIcons
import com.jetbrains.python.packaging.common.PythonOutdatedPackage
import com.jetbrains.python.sdk.basePath
import com.jetbrains.python.sdk.createSdk
import com.jetbrains.python.sdk.getOrCreateAdditionalData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus.Internal
import java.nio.file.Path
import java.util.regex.Pattern
import kotlin.io.path.pathString

// TODO: Provide a special icon for poetry
val POETRY_ICON = PythonIcons.Python.Origami

@Internal
fun suggestedSdkName(basePath: Path): @NlsSafe String = "Poetry (${PathUtil.getFileName(basePath.pathString)})"

/**
 * Sets up the poetry environment under the modal progress window.
 *
 * The poetry is associated with the first valid object from this list:
 *
 * 1. New project specified by [newProjectPath]
 * 2. Existing module specified by [module]
 * 3. Existing project specified by [project]
 *
 * @return the SDK for poetry, not stored in the SDK table yet.
 */
@Internal
suspend fun setupPoetrySdkUnderProgress(
  project: Project?,
  module: Module?,
  existingSdks: List<Sdk>,
  newProjectPath: String?,
  python: String?,
  installPackages: Boolean,
  poetryPath: String? = null,
): PyResult<Sdk> {
  val projectPath = (newProjectPath ?: module?.basePath ?: project?.basePath)?.let { Path.of(it) }
                    ?: return PyResult.localizedError("Can't find path to project or module")

  val actualProject = project ?: module?.project
  val pythonExecutablePath = if (actualProject != null) {
    withBackgroundProgress(actualProject, PyBundle.message("python.sdk.dialog.title.setting.up.poetry.environment"), true) {
      setUpPoetry(projectPath, python, installPackages, poetryPath)
    }
  }
  else {
    setUpPoetry(projectPath, python, installPackages, poetryPath)
  }.getOr { return it }

  val result = createSdk(
    sdkHomePath = pythonExecutablePath,
    existingSdks = existingSdks,
    associatedProjectPath = projectPath.toString(),
    suggestedSdkName = suggestedSdkName(projectPath),
    sdkAdditionalData = PyPoetrySdkAdditionalData(projectPath)
  )
  return result
}

internal val Sdk.isPoetry: Boolean
  get() = getOrCreateAdditionalData() is PyPoetrySdkAdditionalData

internal fun sdkHomes(sdks: List<Sdk>): Set<String> {
  return sdks.mapNotNull { it.homePath }.toSet()
}

internal fun allModules(project: Project?): List<Module> {
  return project?.let {
    ModuleUtil.getModulesOfType(it, PythonModuleTypeBase.getInstance())
  }?.sortedBy { it.name } ?: emptyList()
}

private suspend fun setUpPoetry(projectPath: Path, python: String?, installPackages: Boolean, poetryPath: String? = null): PyResult<Path> {
  val poetryExecutablePathString = when (poetryPath) {
    is String -> poetryPath
    else -> {
      val pyProjectToml = withContext(Dispatchers.IO) { StandardFileSystems.local().findFileByPath(projectPath.toString())?.findChild(PY_PROJECT_TOML) }
      val init = pyProjectToml?.let { getPyProjectTomlForPoetry(it) } == null
      setupPoetry(projectPath, python, installPackages, init).getOr { return it }
    }
  }

  return PyResult.success(Path.of(getPythonExecutable(poetryExecutablePathString)))
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