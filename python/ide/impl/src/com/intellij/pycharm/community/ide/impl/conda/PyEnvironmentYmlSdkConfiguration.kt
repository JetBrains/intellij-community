// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.conda

import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.io.toNioPathOrNull
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.pycharm.community.ide.impl.PyCharmCommunityCustomizationBundle
import com.intellij.pycharm.community.ide.impl.configuration.PySdkConfigurationCollector
import com.intellij.pycharm.community.ide.impl.configuration.PySdkConfigurationCollector.CondaEnvResult
import com.intellij.pycharm.community.ide.impl.configuration.PySdkConfigurationCollector.InputData
import com.intellij.pycharm.community.ide.impl.configuration.PySdkConfigurationCollector.Source
import com.intellij.pycharm.community.ide.impl.configuration.ui.PyAddNewCondaEnvFromFilePanel
import com.intellij.pycharm.community.ide.impl.findEnvOrNull
import com.intellij.python.common.tools.ToolId
import com.intellij.python.community.execService.BinOnEel
import com.intellij.util.FileName
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.jetbrains.python.PyBundle
import com.jetbrains.python.configuration.PyConfigurableInterpreterList
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.getOrNull
import com.jetbrains.python.onSuccess
import com.jetbrains.python.packaging.conda.environmentYml.CondaEnvironmentYmlSdkUtils
import com.jetbrains.python.packaging.conda.environmentYml.format.CondaEnvironmentYmlParser
import com.jetbrains.python.pathValidation.PlatformAndRoot
import com.jetbrains.python.pathValidation.ValidationRequest
import com.jetbrains.python.pathValidation.validateExecutableFile
import com.jetbrains.python.run.PythonInterpreterTargetEnvironmentFactory
import com.jetbrains.python.sdk.PythonSdkUpdater
import com.jetbrains.python.sdk.basePath
import com.jetbrains.python.sdk.conda.PyCondaSdkCustomizer
import com.jetbrains.python.sdk.conda.createCondaSdkAlongWithNewEnv
import com.jetbrains.python.sdk.conda.createCondaSdkFromExistingEnv
import com.jetbrains.python.sdk.conda.execution.CondaExecutor
import com.jetbrains.python.sdk.conda.suggestCondaPath
import com.jetbrains.python.sdk.configuration.*
import com.jetbrains.python.sdk.findAmongRoots
import com.jetbrains.python.sdk.flavors.conda.NewCondaEnvRequest
import com.jetbrains.python.sdk.flavors.conda.PyCondaCommand
import com.jetbrains.python.sdk.flavors.conda.PyCondaEnv
import com.jetbrains.python.sdk.flavors.conda.PyCondaEnvIdentity
import com.jetbrains.python.sdk.setAssociationToModuleAsync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.SystemDependent
import java.nio.file.Path
import kotlin.io.path.name


/**
 * This class only supports local, not remote target.
 * TODO: Support remote target (ie \\wsl)
 */
@ApiStatus.Internal
class PyEnvironmentYmlSdkConfiguration : PyProjectSdkConfigurationExtension {

  override val toolId: ToolId = CONDA_TOOL_ID

  override suspend fun checkEnvironmentAndPrepareSdkCreator(module: Module): CreateSdkInfo? = prepareSdkCreator(
    { checkManageableEnv(module, it) }
  ) { envExists ->
    { needsConfirmation -> createAndAddSdk(module, if (needsConfirmation) Source.CONFIGURATOR else Source.INSPECTION, envExists) }
  }

  override fun asPyProjectTomlSdkConfigurationExtension(): PyProjectTomlConfigurationExtension? = null

  private suspend fun checkManageableEnv(module: Module, checkExistence: CheckExistence): EnvCheckerResult = withBackgroundProgress(module.project, PyBundle.message("python.sdk.validating.environment")) {
    val condaPath = withContext(Dispatchers.IO) {
      suggestCondaPath()?.let { LocalFileSystem.getInstance().findFileByPath(it) }
    }
    val canManage = condaPath != null
    val intentionName = PyCharmCommunityCustomizationBundle.message("sdk.create.condaenv.suggestion")
    val envNotFound = EnvCheckerResult.EnvNotFound(intentionName)

    when {
      canManage && checkExistence -> {
        getCondaEnvIdentity(module, condaPath.path)?.let { env ->
          val binaryToExec = BinOnEel(Path.of(condaPath.path))
          CondaExecutor.getPythonInfo(binaryToExec, env).findEnvOrNull(intentionName)
        } ?: envNotFound
      }
      canManage -> if (getEnvironmentYml(module) != null) envNotFound else EnvCheckerResult.CannotConfigure
      else -> EnvCheckerResult.CannotConfigure
    }
  }

  private fun getEnvironmentYml(module: Module) = listOf(
    CondaEnvironmentYmlSdkUtils.ENV_YAML_FILE_NAME,
    CondaEnvironmentYmlSdkUtils.ENV_YML_FILE_NAME,
  ).firstNotNullOfOrNull { findAmongRoots(module, it) }

  private suspend fun createAndAddSdk(module: Module, source: Source, envExists: Boolean): PyResult<Sdk?> {
    val targetConfig = PythonInterpreterTargetEnvironmentFactory.getTargetModuleResidesOn(module)
    if (targetConfig != null) {
      // Remote targets aren't supported yet
      return PyResult.localizedError(PyCharmCommunityCustomizationBundle.message("sdk.remote.target.are.not.supported.for.conda.environment"))
    }

    // Again: only local conda is supported for now
    val condaExecutable = suggestCondaPath()?.let { LocalFileSystem.getInstance().findFileByPath(it) }
    val sdk = if ((envExists || source == Source.INSPECTION) && validateCondaPath(condaExecutable?.path, PlatformAndRoot.local) == null) {
      PySdkConfigurationCollector.logCondaEnvDialogSkipped(module.project, source, executableToEventField(condaExecutable?.path))
      createAndAddCondaEnv(module, condaExecutable!!.path, null)
    }
    else {
      val envData = askForEnvData(module, source, condaExecutable) ?: return PyResult.success(null)
      createAndAddCondaEnv(module, envData.condaPath, envData.environmentYmlPath)
    }

    return sdk.onSuccess { sdk -> sdk.let { PythonSdkUpdater.scheduleUpdate(it, module.project) } }
  }

  private suspend fun askForEnvData(module: Module, source: Source, condaExecutable: VirtualFile?) = withContext(Dispatchers.Default) {
    val environmentYml = getEnvironmentYml(module) ?: return@withContext null
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

  private suspend fun createAndAddCondaEnv(module: Module, condaExecutable: String, environmentYml: String?): PyResult<Sdk> {
    thisLogger().debug("Creating conda environment")

    val sdk = if (environmentYml == null) {
      useExistingCondaEnv(module, condaExecutable)
    }
    else {
      createCondaEnv(module.project, condaExecutable, environmentYml).also {
        PySdkConfigurationCollector.logCondaEnv(module.project, CondaEnvResult.CREATED)
      }
    }.getOr { return it }

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

  private suspend fun useExistingCondaEnv(module: Module, condaExecutable: String): PyResult<Sdk> {
    val project = module.project
    return PyResult.success(PyCondaCommand(condaExecutable, null).createCondaSdkFromExistingEnv(
      getCondaEnvIdentity(module, condaExecutable)
      ?: return PyResult.localizedError(PyCharmCommunityCustomizationBundle.message("sdk.cannot.use.existing.conda.environment")),
      PyConfigurableInterpreterList.getInstance(project).model.sdks.toList(),
      project
    ))
  }

  private suspend fun getCondaEnvIdentity(module: Module, condaExecutable: String): PyCondaEnvIdentity? {
    val environmentYml = getEnvironmentYml(module)
    val envName = environmentYml?.let { CondaEnvironmentYmlParser.readNameFromFile(it) }
    val envPrefix = environmentYml?.let { CondaEnvironmentYmlParser.readPrefixFromFile(it) }
    val shouldGuessEnvPrefix = envName == null && envPrefix == null
    val binaryToExec = BinOnEel(Path.of(condaExecutable))
    return PyCondaEnv.getEnvs(binaryToExec).getOr { return null }.firstOrNull {
      val envIdentity = it.envIdentity
      when (envIdentity) {
        is PyCondaEnvIdentity.NamedEnv -> envIdentity.envName == envName
        is PyCondaEnvIdentity.UnnamedEnv -> if (shouldGuessEnvPrefix) {
          val envPath = Path.of(envIdentity.envPath)
          val sameModule = module.basePath?.toNioPathOrNull() == envPath.parent
          !envIdentity.isBase && sameModule && module.findAmongRoots(FileName(Path.of(envIdentity.envPath).name)) != null
        }
        else envIdentity.envPath == envPrefix
      }
    }?.envIdentity
  }

  private suspend fun createCondaEnv(project: Project, condaExecutable: String, environmentYml: String): PyResult<Sdk> {
    val binaryToExec = BinOnEel(Path.of(condaExecutable))
    val existingEnvs = PyCondaEnv.getEnvs(binaryToExec).getOrNull() ?: emptyList()

    val existingSdks = PyConfigurableInterpreterList.getInstance(project).model.sdks
    val newCondaEnvInfo = NewCondaEnvRequest.LocalEnvByLocalEnvironmentFile(Path.of(environmentYml),
                                                                            existingEnvs)
    val sdk = PyCondaCommand(condaExecutable, null)
      .createCondaSdkAlongWithNewEnv(newCondaEnvInfo, Dispatchers.EDT, existingSdks.toList(), project).getOr {
        PySdkConfigurationCollector.logCondaEnv(project, CondaEnvResult.CREATION_FAILURE)
        thisLogger().warn("Exception during creating conda environment $it")
        return it
      }

    PySdkConfigurationCollector.logCondaEnv(project, CondaEnvResult.CREATED)
    return PyResult.success(sdk)
  }
}

@RequiresBackgroundThread
@ApiStatus.Internal
fun validateCondaPath(
  condaExecutable: @SystemDependent String?,
  platformAndRoot: PlatformAndRoot,
): ValidationInfo? {
  return validateExecutableFile(
    ValidationRequest(
      condaExecutable,
      PyBundle.message("python.add.sdk.conda.executable.path.is.empty"),
      platformAndRoot,
      null
    ))
}
