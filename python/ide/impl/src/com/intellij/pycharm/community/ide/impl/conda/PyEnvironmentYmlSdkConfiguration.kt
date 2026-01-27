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
import com.intellij.pycharm.community.ide.impl.findEnvOrNull
import com.intellij.python.common.tools.ToolId
import com.intellij.python.community.execService.BinOnEel
import com.intellij.util.FileName
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.jetbrains.python.PyBundle
import com.jetbrains.python.PythonBinary
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
import com.jetbrains.python.sdk.service.PySdkService.Companion.pySdkService
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
internal class PyEnvironmentYmlSdkConfiguration : PyProjectSdkConfigurationExtension {

  override val toolId: ToolId = CONDA_TOOL_ID

  override suspend fun checkEnvironmentAndPrepareSdkCreator(module: Module, venvsInModule: List<PythonBinary>): CreateSdkInfo? = prepareSdkCreator(
    { checkManageableEnv(module, it) }
  ) { envExists -> { createAndAddSdk(module, envExists) } }

  override fun asPyProjectTomlSdkConfigurationExtension(): PyProjectTomlConfigurationExtension? = null

  private suspend fun checkManageableEnv(module: Module, checkExistence: CheckExistence): EnvCheckerResult =
    withBackgroundProgress(module.project, PyBundle.message("python.sdk.validating.environment")) {
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

  private suspend fun getEnvironmentYml(module: Module) = listOf(
    CondaEnvironmentYmlSdkUtils.ENV_YAML_FILE_NAME,
    CondaEnvironmentYmlSdkUtils.ENV_YML_FILE_NAME,
  ).firstNotNullOfOrNull { findAmongRoots(module, it) }

  private suspend fun createAndAddSdk(module: Module, envExists: Boolean): PyResult<Sdk> {
    val targetConfig = PythonInterpreterTargetEnvironmentFactory.getTargetModuleResidesOn(module)
    if (targetConfig != null) {
      // Remote targets aren't supported yet
      return PyResult.localizedError(PyCharmCommunityCustomizationBundle.message("sdk.remote.target.are.not.supported.for.conda.environment"))
    }

    // Again: only local conda is supported for now
    val condaExecutable = suggestCondaPath()?.let { LocalFileSystem.getInstance().findFileByPath(it) }
    validateCondaPath(condaExecutable?.path, PlatformAndRoot.local)?.let {
      return PyResult.localizedError(it.message)
    }

    val sdk = createAndAddCondaEnv(module, condaExecutable!!.path, envExists)
    return sdk.onSuccess { sdk -> sdk.let { PythonSdkUpdater.scheduleUpdate(it, module.project) } }
  }

  private suspend fun createAndAddCondaEnv(module: Module, condaExecutable: String, envExists: Boolean): PyResult<Sdk> {
    thisLogger().debug("Creating conda environment")

    val sdk = if (envExists) {
      useExistingCondaEnv(module, condaExecutable)
    }
    else {
      val environmentYml = getEnvironmentYml(module)
                           ?: return PyResult.localizedError(PyCharmCommunityCustomizationBundle.message("sdk.cannot.create.conda.environment.yml.not.found"))
      createCondaEnv(module.project, condaExecutable, environmentYml).also {
        PySdkConfigurationCollector.logCondaEnv(module.project, CondaEnvResult.CREATED)
      }
    }.getOr { return it }

    val shared = PyCondaSdkCustomizer.instance.sharedEnvironmentsByDefault
    val basePath = module.basePath

    withContext(Dispatchers.EDT) {
      this@PyEnvironmentYmlSdkConfiguration.thisLogger()
        .debug("Adding conda environment: ${sdk.homePath}, associated ${shared}}, module path ${basePath})")
      if (!shared) {
        sdk.setAssociationToModuleAsync(module)
      }

      SdkConfigurationUtil.addSdk(sdk)
      module.project.pySdkService.persistSdk(sdk)
    }

    return PyResult.success(sdk)
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

  private suspend fun createCondaEnv(project: Project, condaExecutable: String, environmentYml: VirtualFile): PyResult<Sdk> {
    val binaryToExec = BinOnEel(Path.of(condaExecutable))
    val existingEnvs = PyCondaEnv.getEnvs(binaryToExec, forceRefresh = true).getOrNull() ?: emptyList()

    val existingSdks = PyConfigurableInterpreterList.getInstance(project).model.sdks
    val newCondaEnvInfo = NewCondaEnvRequest.LocalEnvByLocalEnvironmentFile(environmentYml.toNioPath(), existingEnvs)
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
