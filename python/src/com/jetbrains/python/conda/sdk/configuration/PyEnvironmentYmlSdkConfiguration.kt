// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.conda.sdk.configuration

import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.io.toNioPathOrNull
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.python.common.tools.ToolId
import com.intellij.python.community.execService.BinOnEel
import com.intellij.python.community.impl.conda.environmentYml.CondaEnvironmentYmlSdkUtils
import com.intellij.python.community.impl.conda.environmentYml.format.CondaEnvironmentYmlParser
import com.intellij.util.FileName
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.jetbrains.python.PyBundle
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.configuration.PyConfigurableInterpreterList
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.getOrNull
import com.jetbrains.python.onSuccess
import com.jetbrains.python.pathValidation.PlatformAndRoot
import com.jetbrains.python.pathValidation.ValidationRequest
import com.jetbrains.python.pathValidation.validateExecutableFile
import com.jetbrains.python.run.PythonInterpreterTargetEnvironmentFactory
import com.jetbrains.python.sdk.PythonSdkUpdater
import com.jetbrains.python.sdk.add.v2.PathHolder
import com.jetbrains.python.sdk.baseDir
import com.jetbrains.python.sdk.conda.PyCondaSdkCustomizer
import com.jetbrains.python.sdk.conda.createCondaSdkAlongWithNewEnv
import com.jetbrains.python.sdk.conda.createCondaSdkFromExistingEnvironment
import com.jetbrains.python.sdk.conda.execution.CondaExecutor
import com.jetbrains.python.sdk.conda.findCondaLocal
import com.jetbrains.python.sdk.configuration.CONDA_TOOL_ID
import com.jetbrains.python.sdk.configuration.CreateSdkInfo
import com.jetbrains.python.sdk.configuration.EnvCheckerResult
import com.jetbrains.python.sdk.configuration.PyProjectSdkConfigurationExtension
import com.jetbrains.python.sdk.configuration.PyProjectTomlConfigurationExtension
import com.jetbrains.python.sdk.configuration.PySdkConfigurationCollector
import com.jetbrains.python.sdk.configuration.PySdkConfigurationCollector.CondaEnvResult
import com.jetbrains.python.sdk.configuration.findEnvOrNull
import com.jetbrains.python.sdk.configuration.prepareSdkCreator
import com.jetbrains.python.sdk.findAmongRoots
import com.jetbrains.python.sdk.flavors.conda.NewCondaEnvRequest
import com.jetbrains.python.sdk.flavors.conda.PyCondaCommand
import com.jetbrains.python.sdk.flavors.conda.PyCondaEnv
import com.jetbrains.python.sdk.flavors.conda.PyCondaEnvIdentity
import com.jetbrains.python.sdk.setAssociationToModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.SystemDependent
import java.nio.file.Path
import kotlin.io.path.name
import kotlin.io.path.pathString


/**
 * This class only supports local, not remote target.
 * TODO: Support remote target (ie \\wsl)
 */
internal class PyEnvironmentYmlSdkConfiguration : PyProjectSdkConfigurationExtension {

  override val toolId: ToolId = CONDA_TOOL_ID

  override suspend fun checkEnvironmentAndPrepareSdkCreator(module: Module, venvsInModule: List<PythonBinary>): CreateSdkInfo? =
    prepareSdkCreator(
      { checkManageableEnv(module) }
    ) { envExists -> { createAndAddSdk(module, envExists) } }

  override fun asPyProjectTomlSdkConfigurationExtension(): PyProjectTomlConfigurationExtension? = null

  private suspend fun checkManageableEnv(module: Module): EnvCheckerResult =
    withBackgroundProgress(module.project, PyBundle.message("python.sdk.validating.environment")) {
      val condaPath = findCondaLocal()
      val canManage = condaPath != null
      val intentionName = PyBundle.message("sdk.create.condaenv.suggestion")
      val envNotFound = EnvCheckerResult.EnvNotFound(intentionName)

      if (canManage) {
        val envExistenceResult = getCondaEnvIdentity(module, condaPath)?.let { env ->
          val binaryToExec = BinOnEel(condaPath.path)
          CondaExecutor.getPythonInfo(binaryToExec, env).findEnvOrNull(intentionName)
        }

        envExistenceResult ?: if (getEnvironmentYml(module) != null) envNotFound else EnvCheckerResult.CannotConfigure
      }
      else EnvCheckerResult.CannotConfigure
    }

  private suspend fun getEnvironmentYml(module: Module) = listOf(
    CondaEnvironmentYmlSdkUtils.ENV_YAML_FILE_NAME,
    CondaEnvironmentYmlSdkUtils.ENV_YML_FILE_NAME,
  ).firstNotNullOfOrNull { findAmongRoots(module, it) }

  private suspend fun createAndAddSdk(module: Module, envExists: Boolean): PyResult<Sdk> {
    val targetConfig = PythonInterpreterTargetEnvironmentFactory.getTargetModuleResidesOn(module)
    if (targetConfig != null) {
      // Remote targets aren't supported yet
      return PyResult.localizedError(PyBundle.message("sdk.remote.target.are.not.supported.for.conda.environment"))
    }

    // Again: only local conda is supported for now
    val condaExecutable = findCondaLocal()
    validateCondaPath(condaExecutable?.path?.pathString, PlatformAndRoot.local)?.let {
      return PyResult.localizedError(it.message)
    }

    val sdk = createAndAddCondaEnv(module, condaExecutable!!, envExists)
    return sdk.onSuccess { sdk -> sdk.let { PythonSdkUpdater.scheduleUpdate(it, module.project) } }
  }

  private suspend fun createAndAddCondaEnv(module: Module, condaExecutable: PathHolder.Eel, envExists: Boolean): PyResult<Sdk> {
    thisLogger().debug("Creating conda environment")

    val sdk = if (envExists) {
      useExistingCondaEnv(module, condaExecutable)
    }
    else {
      val environmentYml = getEnvironmentYml(module)
                           ?: return PyResult.localizedError(PyBundle.message("sdk.cannot.create.conda.environment.yml.not.found"))
      createCondaEnv(module.project, condaExecutable, environmentYml).also {
        PySdkConfigurationCollector.logCondaEnv(module.project, CondaEnvResult.CREATED)
      }
    }.getOr { return it }

    val shared = PyCondaSdkCustomizer.instance.sharedEnvironmentsByDefault
    val basePath = module.baseDir?.path

    withContext(Dispatchers.EDT) {
      this@PyEnvironmentYmlSdkConfiguration.thisLogger()
        .debug("Adding conda environment: ${sdk.homePath}, associated ${shared}}, module path ${basePath})")
      if (!shared) {
        sdk.setAssociationToModule(module)
      }
    }

    return PyResult.success(sdk)
  }

  private suspend fun useExistingCondaEnv(module: Module, condaExecutable: PathHolder.Eel): PyResult<Sdk> {
    return PyCondaCommand(condaExecutable.path.pathString, null).createCondaSdkFromExistingEnvironment(
      getCondaEnvIdentity(module, condaExecutable)
      ?: return PyResult.localizedError(PyBundle.message("sdk.cannot.use.existing.conda.environment")),
      PyConfigurableInterpreterList.getInstance(module.project).model.sdks.toList(),
    )
  }

  private suspend fun getCondaEnvIdentity(module: Module, condaExecutable: PathHolder.Eel): PyCondaEnvIdentity? {
    val environmentYml = getEnvironmentYml(module)
    val envName = environmentYml?.let { CondaEnvironmentYmlParser.readNameFromFile(it) }
    val envPrefix = environmentYml?.let { CondaEnvironmentYmlParser.readPrefixFromFile(it) }
    val shouldGuessEnvPrefix = envName == null && envPrefix == null
    val binaryToExec = BinOnEel(condaExecutable.path)
    return PyCondaEnv.getEnvs(binaryToExec).getOr { return null }.firstOrNull {
      val envIdentity = it.envIdentity
      when (envIdentity) {
        is PyCondaEnvIdentity.NamedEnv -> envIdentity.envName == envName
        is PyCondaEnvIdentity.UnnamedEnv -> if (shouldGuessEnvPrefix) {
          val envPath = Path.of(envIdentity.envPath)
          val sameModule = module.baseDir?.path?.toNioPathOrNull() == envPath.parent
          !envIdentity.isBase && sameModule && module.findAmongRoots(FileName(Path.of(envIdentity.envPath).name)) != null
        }
        else envIdentity.envPath == envPrefix
      }
    }?.envIdentity
  }

  private suspend fun createCondaEnv(project: Project, condaExecutable: PathHolder.Eel, environmentYml: VirtualFile): PyResult<Sdk> {
    val binaryToExec = BinOnEel(condaExecutable.path)
    val existingEnvs = PyCondaEnv.getEnvs(binaryToExec, forceRefresh = true).getOrNull() ?: emptyList()

    val existingSdks = PyConfigurableInterpreterList.getInstance(project).model.sdks
    val newCondaEnvInfo = NewCondaEnvRequest.LocalEnvByLocalEnvironmentFile(environmentYml.toNioPath(), existingEnvs)
    val sdk = PyCondaCommand(condaExecutable.path.pathString, null)
      .createCondaSdkAlongWithNewEnv(newCondaEnvInfo, existingSdks.toList()).getOr {
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
