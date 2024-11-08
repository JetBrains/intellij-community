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
import com.jetbrains.python.sdk.*
import com.jetbrains.python.icons.PythonIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus.Internal
import java.io.FileNotFoundException
import java.nio.file.Path
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
): Result<Sdk> {
  val projectPath = newProjectPath ?: module?.basePath ?: project?.basePath
                    ?: return Result.failure(FileNotFoundException("Can't find path to project or module"))

  val actualProject = project ?: module?.project
  val pythonExecutablePath =  if (actualProject != null) {
      withBackgroundProgress(actualProject, PyBundle.message("python.sdk.dialog.title.setting.up.poetry.environment"), true) {
        setUpPoetry(projectPath, python, installPackages, poetryPath)
      }
  } else {
    setUpPoetry(projectPath, python, installPackages, poetryPath)
  }.getOrElse { return Result.failure(it) }

  return createSdk(pythonExecutablePath, existingSdks, projectPath, suggestedSdkName(Path.of(projectPath)), PyPoetrySdkAdditionalData()).onSuccess { sdk ->
    module?.let { sdk.setAssociationToModule(it) }
  }
}

internal val Sdk.isPoetry: Boolean
  get() = sdkAdditionalData is PyPoetrySdkAdditionalData


internal fun sdkHomes(sdks: List<Sdk>): Set<String> {
  return sdks.mapNotNull { it.homePath }.toSet()
}

internal fun allModules(project: Project?): List<Module> {
  return project?.let {
    ModuleUtil.getModulesOfType(it, PythonModuleTypeBase.getInstance())
  }?.sortedBy { it.name } ?: emptyList()
}

private suspend fun setUpPoetry(projectPathString: String, python: String?, installPackages: Boolean, poetryPath: String? = null): Result<Path> {
  val poetryExecutablePathString = when (poetryPath) {
    is String -> poetryPath
    else -> {
      val pyProjectToml = withContext(Dispatchers.IO) { StandardFileSystems.local().findFileByPath(projectPathString)?.findChild(PY_PROJECT_TOML) }
      val init = pyProjectToml?.let { getPyProjectTomlForPoetry(it) } == null
      setupPoetry(Path.of(projectPathString), python, installPackages, init).getOrElse { return Result.failure(it) }
    }
  }

  return Result.success(Path.of(getPythonExecutable(poetryExecutablePathString)))
}