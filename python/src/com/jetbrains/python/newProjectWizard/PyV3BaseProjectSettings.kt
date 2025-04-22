// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.newProjectWizard

import com.intellij.openapi.GitRepositoryInitializer
import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.jetbrains.python.PyBundle
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.PyError
import com.jetbrains.python.newProject.collector.InterpreterStatisticsInfo
import com.jetbrains.python.sdk.ModuleOrProject
import com.jetbrains.python.sdk.add.v2.PySdkCreator
import com.jetbrains.python.sdk.configurePythonSdk
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Settings each Python project has: [sdkCreator] and [createGitRepository]
 */
class PyV3BaseProjectSettings(var createGitRepository: Boolean = false) {
  lateinit var sdkCreator: PySdkCreator

  suspend fun generateAndGetSdk(module: Module, baseDir: VirtualFile, supportsNotEmptyModuleStructure: Boolean = false): Result<Pair<Sdk, InterpreterStatisticsInfo>, PyError> = coroutineScope {
    val project = module.project
    if (createGitRepository) {
      launch(CoroutineName("Generating git") + Dispatchers.IO) {
        withBackgroundProgress(project, PyBundle.message("new.project.git")) {
          GitRepositoryInitializer.getInstance()?.initRepository(project, baseDir, true) ?: error("No git service available")
        }
      }
    }
    if (supportsNotEmptyModuleStructure) {
      sdkCreator.createPythonModuleStructure(module).getOr { return@coroutineScope it }
    }
    val (sdk: Sdk, interpreterStatistics: InterpreterStatisticsInfo) = getSdkAndInterpreter(module)
      .getOr { return@coroutineScope it }

    configurePythonSdk(project, module, sdk)
    return@coroutineScope Result.success(Pair(sdk, interpreterStatistics))
  }

  private suspend fun getSdkAndInterpreter(module: Module): Result<Pair<Sdk, InterpreterStatisticsInfo>, PyError> =
    sdkCreator.getSdk(ModuleOrProject.ModuleAndProject(module))


  override fun toString(): String {
    return "PyV3ProjectGenerationRequest(createGitRepository=$createGitRepository)"
  }
}
