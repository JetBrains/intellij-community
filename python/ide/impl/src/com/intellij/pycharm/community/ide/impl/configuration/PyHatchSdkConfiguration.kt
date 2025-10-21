// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.configuration

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.platform.util.progress.reportRawProgress
import com.intellij.pycharm.community.ide.impl.PyCharmCommunityCustomizationBundle
import com.intellij.python.hatch.HatchVirtualEnvironment
import com.intellij.python.hatch.PythonVirtualEnvironment
import com.intellij.python.hatch.cli.HatchEnvironment
import com.intellij.python.hatch.getHatchService
import com.intellij.python.sdk.ui.icons.PythonSdkUIIcons
import com.jetbrains.python.PyToolUIInfo
import com.jetbrains.python.ToolId
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.getOrLogException
import com.jetbrains.python.hatch.sdk.createSdk
import com.jetbrains.python.projectModel.hatch.HATCH_TOOL_ID
import com.jetbrains.python.sdk.configuration.*
import com.jetbrains.python.util.runWithModalBlockingOrInBackground
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class PyHatchSdkConfiguration : PyProjectTomlConfigurationExtension {
  companion object {
    private val LOGGER = Logger.getInstance(PyHatchSdkConfiguration::class.java)
  }

  override val toolInfo: PyToolUIInfo = PyToolUIInfo("Hatch", PythonSdkUIIcons.Tools.Hatch)
  override val toolId: ToolId = HATCH_TOOL_ID

  override suspend fun checkEnvironmentAndPrepareSdkCreator(module: Module): CreateSdkInfo? = prepareSdkCreator(
    toolInfo,
    { checkExistence -> checkManageableEnv(module, checkExistence, true) },
  ) { envExists -> { createSdk(module, envExists) } }

  override suspend fun createSdkWithoutPyProjectTomlChecks(module: Module): CreateSdkInfo? = prepareSdkCreator(
    toolInfo,
    { checkExistence -> checkManageableEnv(module, checkExistence, false) },
  ) { envExists -> { createSdk(module, envExists) } }

  override fun asPyProjectTomlSdkConfigurationExtension(): PyProjectTomlConfigurationExtension = this

  private suspend fun checkManageableEnv(
    module: Module, checkExistence: CheckExistence, checkToml: CheckToml,
  ): EnvCheckerResult = reportRawProgress {
    it.text(PyCharmCommunityCustomizationBundle.message("sdk.set.up.hatch.project.analysis"))
    val hatchService = module.getHatchService().getOr { return EnvCheckerResult.CannotConfigure }
    val canManage = if (checkToml) hatchService.isHatchManagedProject().getOrLogException(LOGGER) == true else true
    val intentionName = PyCharmCommunityCustomizationBundle.message("sdk.set.up.hatch.environment")
    val envNotFound = EnvCheckerResult.EnvNotFound(intentionName)

    when {
      canManage && checkExistence -> {
        val defaultEnv = hatchService.findDefaultVirtualEnvironmentOrNull().getOrLogException(LOGGER)?.pythonVirtualEnvironment
        when (defaultEnv) {
          is PythonVirtualEnvironment.Existing -> EnvCheckerResult.EnvFound(defaultEnv.pythonInfo, intentionName)
          is PythonVirtualEnvironment.NotExisting, null -> envNotFound
        }
      }
      canManage -> envNotFound
      else -> EnvCheckerResult.CannotConfigure
    }
  }

  /**
   * Creates SDK for Hatch, it will also create a new Hatch environment and use an existing one.
   *
   * @param module module used to create SDK
   * @param envExists shows whether the environment already exists or a new one should be created
   */
  private fun createSdk(module: Module, envExists: EnvExists): PyResult<Sdk> = runWithModalBlockingOrInBackground(
    project = module.project,
    msg = PyCharmCommunityCustomizationBundle.message("sdk.set.up.hatch.environment")
  ) {
    val hatchService = module.getHatchService().getOr { return@runWithModalBlockingOrInBackground it }

    val environment = if (envExists) {
      val defaultEnv = hatchService.findDefaultVirtualEnvironmentOrNull()
        .mapSuccess { it?.pythonVirtualEnvironment }
        .getOr { return@runWithModalBlockingOrInBackground it }
      when (defaultEnv) {
        is PythonVirtualEnvironment.Existing -> defaultEnv
        is PythonVirtualEnvironment.NotExisting, null -> return@runWithModalBlockingOrInBackground PyResult.localizedError(PyCharmCommunityCustomizationBundle.message("sdk.could.not.find.valid.hatch.environment"))
      }
    }
    else {
      hatchService.createVirtualEnvironment().getOr { return@runWithModalBlockingOrInBackground it }
    }

    val hatchVenv = HatchVirtualEnvironment(HatchEnvironment.DEFAULT, environment)
    val sdk = hatchVenv.createSdk(hatchService.getWorkingDirectoryPath())
    sdk
  }

}