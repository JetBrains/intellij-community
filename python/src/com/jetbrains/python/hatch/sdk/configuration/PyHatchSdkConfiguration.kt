// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.hatch.sdk.configuration

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.platform.util.progress.reportRawProgress
import com.intellij.python.common.tools.ToolId
import com.intellij.python.hatch.HatchVirtualEnvironment
import com.intellij.python.hatch.PythonVirtualEnvironment
import com.intellij.python.hatch.cli.HatchEnvironment
import com.intellij.python.hatch.getHatchService
import com.intellij.python.hatch.impl.HATCH_TOOL_ID
import com.jetbrains.python.PyBundle
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.hatch.sdk.createSdk
import com.jetbrains.python.onSuccess
import com.jetbrains.python.orLogException
import com.jetbrains.python.sdk.configuration.CheckToml
import com.jetbrains.python.sdk.configuration.CreateSdkInfo
import com.jetbrains.python.sdk.configuration.EnvCheckerResult
import com.jetbrains.python.sdk.configuration.EnvExists
import com.jetbrains.python.sdk.configuration.PyProjectTomlConfigurationExtension
import com.jetbrains.python.sdk.configuration.prepareSdkCreator
import com.jetbrains.python.sdk.service.PySdkService.Companion.pySdkService
import com.jetbrains.python.sdk.setAssociationToModule
import com.jetbrains.python.util.runWithModalBlockingOrInBackground

internal class PyHatchSdkConfiguration : PyProjectTomlConfigurationExtension {
  companion object {
    private val LOGGER = Logger.getInstance(PyHatchSdkConfiguration::class.java)
  }

  override val toolId: ToolId = HATCH_TOOL_ID

  override suspend fun checkEnvironmentAndPrepareSdkCreator(module: Module, venvsInModule: List<PythonBinary>): CreateSdkInfo? =
      prepareSdkCreator(
          { checkManageableEnv(module, true) },
      ) { envExists -> { createSdk(module, envExists) } }

  override suspend fun createSdkWithoutPyProjectTomlChecks(module: Module, venvsInModule: List<PythonBinary>): CreateSdkInfo? =
      prepareSdkCreator(
          { checkManageableEnv(module, false) },
      ) { envExists -> { createSdk(module, envExists) } }

  override fun asPyProjectTomlSdkConfigurationExtension(): PyProjectTomlConfigurationExtension = this

  private suspend fun checkManageableEnv(
      module: Module, checkToml: CheckToml,
  ): EnvCheckerResult = reportRawProgress {
      it.text(PyBundle.message("sdk.set.up.hatch.project.analysis"))
      val hatchService = module.getHatchService().getOr { return EnvCheckerResult.CannotConfigure }
      val canManage = if (checkToml) hatchService.isHatchManagedProject() else true
      val intentionName = PyBundle.message("sdk.set.up.hatch.environment")
      val envNotFound = EnvCheckerResult.EnvNotFound(intentionName)

      if (canManage) {
          val defaultEnv = hatchService.findDefaultVirtualEnvironmentOrNull().orLogException(LOGGER)?.pythonVirtualEnvironment
          when (defaultEnv) {
              is PythonVirtualEnvironment.Existing -> EnvCheckerResult.EnvFound(defaultEnv.pythonInfo, intentionName)
              is PythonVirtualEnvironment.NotExisting, null -> envNotFound
          }
      } else EnvCheckerResult.CannotConfigure
  }

  /**
   * Creates SDK for Hatch, it will also create a new Hatch environment and use an existing one.
   *
   * @param module module used to create SDK
   * @param envExists shows whether the environment already exists or a new one should be created
   */
  private fun createSdk(module: Module, envExists: EnvExists): PyResult<Sdk> = runWithModalBlockingOrInBackground(
      project = module.project,
      msg = PyBundle.message("sdk.set.up.hatch.environment")
  ) {
      val hatchService = module.getHatchService().getOr { return@runWithModalBlockingOrInBackground it }

      val environment = if (envExists) {
          val defaultEnv = hatchService.findDefaultVirtualEnvironmentOrNull()
              .mapSuccess { it?.pythonVirtualEnvironment }
              .getOr { return@runWithModalBlockingOrInBackground it }
          when (defaultEnv) {
              is PythonVirtualEnvironment.Existing -> defaultEnv
              is PythonVirtualEnvironment.NotExisting, null -> return@runWithModalBlockingOrInBackground PyResult.localizedError(
                  PyBundle.message("sdk.could.not.find.valid.hatch.environment")
              )
          }
      } else {
          hatchService.createVirtualEnvironment().getOr { return@runWithModalBlockingOrInBackground it }
      }

      val hatchVenv = HatchVirtualEnvironment(HatchEnvironment.Companion.DEFAULT, environment)
      val sdk = hatchVenv.createSdk(hatchService.getWorkingDirectoryPath()).onSuccess { sdk ->
          sdk.setAssociationToModule(module)
          module.project.pySdkService.persistSdk(sdk)
      }
      sdk
  }

}