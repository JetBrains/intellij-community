// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.newProjectWizard

import com.intellij.openapi.GitRepositoryInitializer
import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.jetbrains.python.PyBundle
import com.jetbrains.python.Result
import com.jetbrains.python.TraceContext
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.newProject.collector.InterpreterStatisticsInfo
import com.jetbrains.python.sdk.ModuleOrProject
import com.jetbrains.python.sdk.add.v2.PySdkCreator
import com.jetbrains.python.sdk.configurePythonSdk
import com.jetbrains.python.sdk.pythonSdkConfigurationMutex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Settings each Python project has: [sdkCreator] and [createGitRepository]
 */
class PyV3BaseProjectSettings(var createGitRepository: Boolean = false) {
  lateinit var sdkCreator: PySdkCreator

  suspend fun generateAndGetSdk(module: Module, baseDir: VirtualFile, supportsNotEmptyModuleStructure: Boolean = false): PyResult<Pair<Sdk, InterpreterStatisticsInfo>> = coroutineScope {
    val project = module.project
    if (createGitRepository) {
      launch(TraceContext(PyBundle.message("trace.context.generating.git")) + Dispatchers.IO) {
        withBackgroundProgress(project, PyBundle.message("new.project.git")) {
          GitRepositoryInitializer.getInstance()?.initRepository(project, baseDir, true) ?: error("No git service available")
        }
      }
    }

    if (supportsNotEmptyModuleStructure) {
      withBackgroundProgress(project, PyBundle.message("python.sdk.creating.python.module.structure")) {
        withContext(Dispatchers.IO) {
          sdkCreator.createPythonModuleStructure(module).also {
            VfsUtil.markDirtyAndRefresh(false, true, true, baseDir)
          }
        }
      }.getOr { return@coroutineScope it }
    }

    val sdkResult = pythonSdkConfigurationMutex.withLock {
      val (sdk: Sdk, interpreterStatistics: InterpreterStatisticsInfo) = withBackgroundProgress(project, PyBundle.message("python.sdk.creating.python.sdk")) {
        getSdkAndInterpreter(module)
      }.getOr { return@withLock it }

      configurePythonSdk(project, module, sdk)
      Result.success(Pair(sdk, interpreterStatistics))
    }
    return@coroutineScope sdkResult
  }

  private suspend fun getSdkAndInterpreter(module: Module): PyResult<Pair<Sdk, InterpreterStatisticsInfo>> =
    sdkCreator.getSdk(ModuleOrProject.ModuleAndProject(module))


  override fun toString(): String {
    return "PyV3ProjectGenerationRequest(createGitRepository=$createGitRepository)"
  }
}
