// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.poetry

import com.intellij.execution.ExecutionException
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.PathUtil
import com.jetbrains.python.PyBundle
import com.jetbrains.python.PythonModuleTypeBase
import com.jetbrains.python.sdk.*
import com.jetbrains.python.icons.PythonIcons
import java.nio.file.Path
import kotlin.io.path.pathString

const val POETRY_LOCK: String = "poetry.lock"
const val POETRY_DEFAULT_SOURCE_URL: String = "https://pypi.org/simple"

// TODO: Provide a special icon for poetry
val POETRY_ICON = PythonIcons.Python.Origami


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
fun setupPoetrySdkUnderProgress(project: Project?,
                                module: Module?,
                                existingSdks: List<Sdk>,
                                newProjectPath: String?,
                                python: String?,
                                installPackages: Boolean,
                                poetryPath: String? = null): Sdk? {
  val projectPath = newProjectPath ?: module?.basePath ?: project?.basePath ?: return null
  val task = object : Task.WithResult<String, ExecutionException>(project,
                                                                  PyBundle.message("python.sdk.dialog.title.setting.up.poetry.environment"),
                                                                  true) {
    override fun compute(indicator: ProgressIndicator): String {
      indicator.isIndeterminate = true
      val poetry = when (poetryPath) {
        is String -> poetryPath
        else -> {
          val init = StandardFileSystems.local().findFileByPath(projectPath)?.findChild(PY_PROJECT_TOML)?.let {
            getPyProjectTomlForPoetry(it)
          } == null
          setupPoetry(Path.of(projectPath), python, installPackages, init)
        }
      }
      return getPythonExecutable(poetry)
    }
  }

  return createSdkByGenerateTask(task, existingSdks, null, projectPath, suggestedSdkName(Path.of(projectPath))).apply {
    module?.let { setAssociationToModule(it) }
    isPoetry = true
  }
}

var Sdk.isPoetry: Boolean
  get() = sdkAdditionalData is PyPoetrySdkAdditionalData
  set(value) = setCorrectTypeSdk(this, PyPoetrySdkAdditionalData::class.java, value)

val Module.poetryLock: VirtualFile?
  get() = baseDir?.findChild(POETRY_LOCK)

internal fun allModules(project: Project?): List<Module> {
  return project?.let {
    ModuleUtil.getModulesOfType(it, PythonModuleTypeBase.getInstance())
  }?.sortedBy { it.name } ?: emptyList()
}

internal fun sdkHomes(sdks: List<Sdk>): Set<String> = sdks.mapNotNull { it.homePath }.toSet()