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
import com.jetbrains.python.configuration.PyConfigurableInterpreterList
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.getOrNull
import com.jetbrains.python.onSuccess
import com.jetbrains.python.packaging.conda.environmentYml.CondaEnvironmentYmlSdkUtils
import com.jetbrains.python.pathValidation.PlatformAndRoot
import com.jetbrains.python.run.PythonInterpreterTargetEnvironmentFactory
import com.jetbrains.python.sdk.PythonSdkUpdater
import com.jetbrains.python.sdk.basePath
import com.jetbrains.python.sdk.conda.PyCondaSdkCustomizer
import com.jetbrains.python.sdk.conda.createCondaSdkAlongWithNewEnv
import com.jetbrains.python.sdk.conda.suggestCondaPath
import com.jetbrains.python.sdk.configuration.PyProjectSdkConfigurationExtension
import com.jetbrains.python.sdk.findAmongRoots
import com.jetbrains.python.sdk.flavors.conda.CondaEnvSdkFlavor
import com.jetbrains.python.sdk.flavors.conda.NewCondaEnvRequest
import com.jetbrains.python.sdk.flavors.conda.PyCondaCommand
import com.jetbrains.python.sdk.flavors.conda.PyCondaEnv
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
  override suspend fun createAndAddSdkForConfigurator(module: Module): PyResult<Sdk?> = createAndAddSdk(module, Source.CONFIGURATOR)

  override suspend fun getIntention(module: Module): @IntentionName String? {
    val isReadyToSetup = withContext(Dispatchers.IO) {
      getEnvironmentYml(module) != null &&
      suggestCondaPath()?.let { LocalFileSystem.getInstance().findFileByPath(it) } != null
    }

    return if (isReadyToSetup) PyCharmCommunityCustomizationBundle.message("sdk.create.condaenv.suggestion") else null
  }

  override suspend fun createAndAddSdkForInspection(module: Module): PyResult<Sdk?> = createAndAddSdk(module, Source.INSPECTION)

  private fun getEnvironmentYml(module: Module) = listOf(
    CondaEnvironmentYmlSdkUtils.ENV_YAML_FILE_NAME,
    CondaEnvironmentYmlSdkUtils.ENV_YML_FILE_NAME,
  ).firstNotNullOfOrNull { findAmongRoots(module, it) }

  private suspend fun createAndAddSdk(module: Module, source: Source): PyResult<Sdk?> {
    val targetConfig = PythonInterpreterTargetEnvironmentFactory.getTargetModuleResidesOn(module)
    if (targetConfig != null) {
      // Remote targets aren't supported yet
      return PyResult.success(null)
    }

    val (condaExecutable, environmentYml) = askForEnvData(module, source) ?: return PyResult.success(null)
    return createAndAddCondaEnv(module, condaExecutable, environmentYml).onSuccess { sdk ->
      sdk?.let { PythonSdkUpdater.scheduleUpdate(it, module.project) }
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

  private suspend fun createAndAddCondaEnv(module: Module, condaExecutable: String, environmentYml: String): PyResult<Sdk?> {
    thisLogger().debug("Creating conda environment")

    val sdk = createCondaEnv(module.project, condaExecutable, environmentYml) ?: return PyResult.success(null)
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

    return PyResult.success(sdk)
  }

  private fun executableToEventField(condaExecutable: String?): InputData {
    return if (condaExecutable.isNullOrBlank()) InputData.NOT_FILLED else InputData.SPECIFIED
  }

  private suspend fun createCondaEnv(project: Project, condaExecutable: String, environmentYml: String): Sdk? {
    val existingEnvs = PyCondaEnv.getEnvs(condaExecutable).getOrNull() ?: emptyList()

    val existingSdks = PyConfigurableInterpreterList.getInstance(project).model.sdks
    val newCondaEnvInfo = NewCondaEnvRequest.LocalEnvByLocalEnvironmentFile(Path.of(environmentYml),
                                                                            existingEnvs)
    val sdk = PyCondaCommand(condaExecutable, null)
      .createCondaSdkAlongWithNewEnv(newCondaEnvInfo, Dispatchers.EDT, existingSdks.toList(), project).getOr {
        PySdkConfigurationCollector.logCondaEnv(project, CondaEnvResult.CREATION_FAILURE)
        thisLogger().warn("Exception during creating conda environment $it")

        ShowingMessageErrorSync.emit(it.error)
        return null
      }

    PySdkConfigurationCollector.logCondaEnv(project, CondaEnvResult.CREATED)
    return sdk
  }
}