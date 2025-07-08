// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.conda

import com.intellij.codeInspection.util.IntentionName
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.pycharm.community.ide.impl.PyCharmCommunityCustomizationBundle
import com.intellij.pycharm.community.ide.impl.configuration.PySdkConfigurationCollector
import com.intellij.pycharm.community.ide.impl.configuration.PySdkConfigurationCollector.CondaEnvResult
import com.intellij.pycharm.community.ide.impl.configuration.PySdkConfigurationCollector.InputData
import com.intellij.pycharm.community.ide.impl.configuration.PySdkConfigurationCollector.Source
import com.intellij.pycharm.community.ide.impl.configuration.ui.PyAddNewCondaEnvFromFilePanel
import com.intellij.webcore.packaging.PackageManagementService
import com.intellij.webcore.packaging.PackagesNotificationPanel
import com.jetbrains.python.configuration.PyConfigurableInterpreterList
import com.jetbrains.python.getOrNull
import com.jetbrains.python.onFailure
import com.jetbrains.python.packaging.conda.environmentYml.CondaEnvironmentYmlSdkUtils
import com.jetbrains.python.pathValidation.PlatformAndRoot
import com.jetbrains.python.run.PythonInterpreterTargetEnvironmentFactory
import com.jetbrains.python.sdk.PythonSdkUpdater
import com.jetbrains.python.sdk.basePath
import com.jetbrains.python.sdk.conda.PyCondaSdkCustomizer
import com.jetbrains.python.sdk.conda.createCondaSdkAlongWithNewEnv
import com.jetbrains.python.sdk.conda.execution.CondaExecutor
import com.jetbrains.python.sdk.conda.suggestCondaPath
import com.jetbrains.python.sdk.configuration.PyProjectSdkConfigurationExtension
import com.jetbrains.python.sdk.findAmongRoots
import com.jetbrains.python.sdk.flavors.conda.CondaEnvSdkFlavor
import com.jetbrains.python.sdk.flavors.conda.NewCondaEnvRequest
import com.jetbrains.python.sdk.flavors.conda.PyCondaCommand
import com.jetbrains.python.sdk.setAssociationToModuleAsync
import com.jetbrains.python.util.ShowingMessageErrorSync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path


/**
 * This class only supports local, not remote target.
 * TODO: Support remote target (ie \\wsl)
 */
internal class PyEnvironmentYmlSdkConfiguration : PyProjectSdkConfigurationExtension {
  override suspend fun createAndAddSdkForConfigurator(module: Module): Sdk? = createAndAddSdk(module, Source.CONFIGURATOR)

  override suspend fun getIntention(module: Module): @IntentionName String? = getEnvironmentYml(module)?.let {
    PyCharmCommunityCustomizationBundle.message("sdk.create.condaenv.suggestion")
  }

  override suspend fun createAndAddSdkForInspection(module: Module): Sdk? = createAndAddSdk(module, Source.INSPECTION)

  private fun getEnvironmentYml(module: Module) = listOf(
    CondaEnvironmentYmlSdkUtils.ENV_YAML_FILE_NAME,
    CondaEnvironmentYmlSdkUtils.ENV_YML_FILE_NAME,
  ).firstNotNullOfOrNull { findAmongRoots(module, it) }

  private suspend fun createAndAddSdk(module: Module, source: Source): Sdk? {
    val targetConfig = PythonInterpreterTargetEnvironmentFactory.getTargetModuleResidesOn(module)
    if (targetConfig != null) {
      // Remote targets aren't supported yet
      return null
    }

    val (condaExecutable, environmentYml) = askForEnvData(module, source) ?: return null
    return createAndAddCondaEnv(module, condaExecutable, environmentYml)?.also {
      PythonSdkUpdater.scheduleUpdate(it, module.project)
    }
  }

  private suspend fun askForEnvData(module: Module, source: Source) = withContext(Dispatchers.Default) {
    val environmentYml = getEnvironmentYml(module) ?: return@withContext null
    // Again: only local conda is supported for now
    val condaExecutable = suggestCondaPath()?.let { LocalFileSystem.getInstance().findFileByPath(it) }

    if (source == Source.INSPECTION && CondaEnvSdkFlavor.validateCondaPath(condaExecutable?.path, PlatformAndRoot.local) == null) {
      PySdkConfigurationCollector.logCondaEnvDialogSkipped(module.project, source, executableToEventField(condaExecutable?.path))
      return@withContext PyAddNewCondaEnvFromFilePanel.Data(condaExecutable!!.path, environmentYml.path)
    }

    var permitted = false
    var envData: PyAddNewCondaEnvFromFilePanel.Data? = null

    withContext(Dispatchers.EDT) {
      val dialog = CondaCreateSdkDialog(module, condaExecutable, environmentYml)

      permitted = dialog.showAndGet()
      envData = dialog.envData

      this@PyEnvironmentYmlSdkConfiguration.thisLogger().debug("Dialog exit code: ${dialog.exitCode}, $permitted")
    }

    PySdkConfigurationCollector.logCondaEnvDialog(module.project, permitted, source, executableToEventField(envData?.condaPath))
    if (permitted) envData else null
  }

  private suspend fun createAndAddCondaEnv(module: Module, condaExecutable: String, environmentYml: String): Sdk? {
    thisLogger().debug("Creating conda environment")

    val sdk = createCondaEnv(module.project, condaExecutable, environmentYml) ?: return null
    PySdkConfigurationCollector.logCondaEnv(module.project, CondaEnvResult.CREATED)

    val shared = PyCondaSdkCustomizer.instance.sharedEnvironmentsByDefault
    val basePath = module.basePath

    withContext(Dispatchers.EDT) {
      this@PyEnvironmentYmlSdkConfiguration.thisLogger().debug("Adding conda environment: ${sdk.homePath}, associated ${shared}}, module path ${basePath})")
      if (!shared) {
        sdk.setAssociationToModuleAsync(module)
      }

      SdkConfigurationUtil.addSdk(sdk)
    }

    return sdk
  }

  private fun executableToEventField(condaExecutable: String?): InputData {
    return if (condaExecutable.isNullOrBlank()) InputData.NOT_FILLED else InputData.SPECIFIED
  }

  private suspend fun createCondaEnv(project: Project, condaExecutable: String, environmentYml: String): Sdk? {
    val condaEnvironmentsBefore = safelyListCondaEnvironments(project, condaExecutable) ?: return null

    val existingSdks = PyConfigurableInterpreterList.getInstance(project).model.sdks
    val newCondaEnvInfo = NewCondaEnvRequest.LocalEnvByLocalEnvironmentFile(Path.of(environmentYml))
    val sdk = PyCondaCommand(condaExecutable, null)
      .createCondaSdkAlongWithNewEnv(newCondaEnvInfo, Dispatchers.EDT, existingSdks.toList(), project).getOr {
        PySdkConfigurationCollector.logCondaEnv(project, CondaEnvResult.CREATION_FAILURE)
        thisLogger().warn("Exception during creating conda environment $it")
        val message = PackageManagementService.ErrorDescription.fromMessage(it.error.message)
        message?.let { description ->
          withContext(Dispatchers.EDT) {
            PackagesNotificationPanel.showError(
              PyCharmCommunityCustomizationBundle.message("sdk.create.condaenv.exception.dialog.title"),
              description
            )
          }
        }
        return null
      }

    val condaEnvironmentsAfter = safelyListCondaEnvironments(project, condaExecutable) ?: return null
    val difference = condaEnvironmentsAfter - condaEnvironmentsBefore.toSet()
    difference.singleOrNull().also {
      if (it == null) {
        PySdkConfigurationCollector.logCondaEnv(
          project,
          if (difference.isEmpty()) CondaEnvResult.NO_LISTING_DIFFERENCE else CondaEnvResult.AMBIGUOUS_LISTING_DIFFERENCE
        )

        thisLogger().warn(
          """
          Several or none conda envs found:
          Before: $condaEnvironmentsBefore
          After: $condaEnvironmentsAfter
          """.trimIndent()
        )
      }
    }
    return sdk

  }

  private suspend fun safelyListCondaEnvironments(project: Project, condaExecutable: String): List<String>? {
    val listEnvs = CondaExecutor.listEnvs(Path.of(condaExecutable)).onFailure {
      PySdkConfigurationCollector.logCondaEnv(project, CondaEnvResult.LISTING_FAILURE)
      it.addMessage(PyCharmCommunityCustomizationBundle.message("sdk.detect.condaenv.exception.dialog.title"))
      ShowingMessageErrorSync.emit(it)
    }
    return listEnvs.getOrNull()?.envs?.toList()
  }
}