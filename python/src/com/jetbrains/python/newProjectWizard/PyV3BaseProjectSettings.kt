// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.newProjectWizard

import com.intellij.openapi.GitRepositoryInitializer
import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.jetbrains.python.PyBundle
import com.jetbrains.python.newProject.collector.InterpreterStatisticsInfo
import com.jetbrains.python.sdk.ModuleOrProject
import com.jetbrains.python.sdk.add.v2.PySdkCreator
import com.jetbrains.python.sdk.pythonSdk
import com.jetbrains.python.sdk.setAssociationToModule
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Settings each Python project has: [sdkCreator] and [createGitRepository]
 */
class PyV3BaseProjectSettings(var createGitRepository: Boolean = false) {
  lateinit var sdkCreator: PySdkCreator

  suspend fun generateAndGetSdk(module: Module, baseDir: VirtualFile): Result<Pair<Sdk, InterpreterStatisticsInfo>> = coroutineScope {
    val project = module.project
    if (createGitRepository) {
      launch(CoroutineName("Generating git") + Dispatchers.IO) {
        withBackgroundProgress(project, PyBundle.message("new.project.git")) {
          GitRepositoryInitializer.getInstance()?.initRepository(project, baseDir, true) ?: error("No git service available")
        }
      }
    }
    val (sdk: Sdk, interpreterStatistics: InterpreterStatisticsInfo) = getSdkAndInterpreter(module).getOrElse { return@coroutineScope Result.failure(it) }
    sdk.setAssociationToModule(module)
    module.pythonSdk = sdk
    return@coroutineScope Result.success(Pair(sdk, interpreterStatistics))
  }

  private suspend fun getSdkAndInterpreter(module: Module): Result<Pair<Sdk, InterpreterStatisticsInfo>> =
    sdkCreator.getSdk(ModuleOrProject.ModuleAndProject(module))


  override fun toString(): String {
    return "PyV3ProjectGenerationRequest(createGitRepository=$createGitRepository)"
  }
}
