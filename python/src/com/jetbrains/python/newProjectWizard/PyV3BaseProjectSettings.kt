// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.newProjectWizard

import com.intellij.openapi.GitRepositoryInitializer
import com.intellij.openapi.application.EDT
import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.jetbrains.python.PyBundle
import com.jetbrains.python.newProject.collector.InterpreterStatisticsInfo
import com.jetbrains.python.newProject.collector.PythonNewProjectWizardCollector.logPythonNewProjectGenerated
import com.jetbrains.python.sdk.ModuleOrProject
import com.jetbrains.python.sdk.add.PySdkCreator
import com.jetbrains.python.sdk.pythonSdk
import com.jetbrains.python.sdk.setAssociationToModule
import com.jetbrains.python.statistics.version
import kotlinx.coroutines.*

/**
 * Settings each Python project has: [sdkCreator] and [createGitRepository]
 */
class PyV3BaseProjectSettings(var createGitRepository: Boolean = false) {
  lateinit var sdkCreator: PySdkCreator

  suspend fun generateAndGetSdk(module: Module, baseDir: VirtualFile): Result<Sdk> = coroutineScope {
    val project = module.project
    if (createGitRepository) {
      launch(CoroutineName("Generating git") + Dispatchers.IO) {
        withBackgroundProgress(project, PyBundle.message("new.project.git")) {
          GitRepositoryInitializer.getInstance()?.initRepository(project, baseDir, true) ?: error("No git service available")
        }
      }
    }
    val (sdk: Sdk, statistics: InterpreterStatisticsInfo?) = getSdkAndInterpreter(module).getOrElse { return@coroutineScope Result.failure(it) }
    sdk.setAssociationToModule(module)
    module.pythonSdk = sdk
    if (statistics != null) {
      logPythonNewProjectGenerated(statistics,
                                   sdk.version,
                                   this::class.java,
                                   emptyList())
    }
    return@coroutineScope Result.success(sdk)
  }

  private suspend fun getSdkAndInterpreter(module: Module): Result<Pair<Sdk, InterpreterStatisticsInfo?>> = withContext(Dispatchers.EDT) {
    val sdk: Sdk = sdkCreator.getSdk(ModuleOrProject.ModuleAndProject(module)).getOrElse { return@withContext Result.failure(it) }
    return@withContext Result.success(Pair<Sdk, InterpreterStatisticsInfo?>(sdk, sdkCreator.createStatisticsInfo()))
  }


  override fun toString(): String {
    return "PyV3ProjectGenerationRequest(createGitRepository=$createGitRepository)"
  }
}
