// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.configuration

import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.util.io.toNioPathOrNull
import com.intellij.openapi.vfs.readText
import com.intellij.pycharm.community.ide.impl.PyCharmCommunityCustomizationBundle
import com.intellij.pycharm.community.ide.impl.findEnvOrNull
import com.intellij.python.common.tools.ToolId
import com.intellij.python.community.impl.uv.common.UV_TOOL_ID
import com.intellij.python.pyproject.PyProjectToml
import com.intellij.python.pyproject.model.api.SuggestedSdk
import com.intellij.python.pyproject.model.api.suggestSdk
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.orLogException
import com.jetbrains.python.onSuccess
import com.jetbrains.python.sdk.*
import com.jetbrains.python.sdk.configuration.*
import com.jetbrains.python.sdk.legacy.PythonSdkUtil
import com.jetbrains.python.sdk.uv.impl.getUvExecutable
import com.jetbrains.python.sdk.uv.setupExistingEnvAndSdk
import com.jetbrains.python.sdk.uv.setupNewUvSdkAndEnv
import com.jetbrains.python.venvReader.tryResolvePath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.io.IOException
import java.nio.file.Path

private val logger = fileLogger()

@ApiStatus.Internal
class PyUvSdkConfiguration : PyProjectTomlConfigurationExtension {
  private val existingSdks by lazy { PythonSdkUtil.getAllSdks() }
  private val context = UserDataHolderBase()

  override val toolId: ToolId = UV_TOOL_ID

  override suspend fun checkEnvironmentAndPrepareSdkCreator(module: Module): CreateSdkInfo? = prepareSdkCreator(
    { checkExistence -> checkManageableEnv(module, checkExistence, true) }
  ) { envExists -> { createUv(module, envExists) } }

  override suspend fun createSdkWithoutPyProjectTomlChecks(module: Module): CreateSdkInfo? = prepareSdkCreator(
    { checkExistence -> checkManageableEnv(module, checkExistence, false) }
  ) { envExists -> { createUv(module, envExists) } }

  override fun asPyProjectTomlSdkConfigurationExtension(): PyProjectTomlConfigurationExtension = this

  /**
   * This method checks whether uv environment exists and whether uv can manage the environment using the following logic:
   *   - If uv is not found on the system, the sdk cannot be configured with uv
   *   - If pyproject.toml check is required
   *     - If pyproject.toml file is found, we check whether we can manage this project
   *     - If there's no pyproject.toml, we assume that we cannot configure the project however,
   *       if we found existing uv environment, we will use it
   *   - If pyproject.toml check shouldn't be performed, then we just check whether the environment exists
   */
  private suspend fun checkManageableEnv(module: Module, checkExistence: CheckExistence, checkToml: CheckToml): EnvCheckerResult {
    getUvExecutable() ?: return EnvCheckerResult.CannotConfigure

    val (canManage, projectName) = if (checkToml) {
      val tomlFile = PyProjectToml.findFile(module)

      val projectName = tomlFile?.let {
        val tomlFileContent = withContext(Dispatchers.IO) {
          try {
            tomlFile.readText()
          }
          catch (e: IOException) {
            logger.debug("Can't read ${tomlFile}", e)
            null
          }
        } ?: return EnvCheckerResult.CannotConfigure
        val tomlContentResult = withContext(Dispatchers.Default) { PyProjectToml.parse(tomlFileContent) }
        val tomlContent = tomlContentResult.orLogException(logger) ?: return EnvCheckerResult.CannotConfigure
        val project = tomlContent.project ?: return EnvCheckerResult.CannotConfigure
        project.name ?: module.name
      }

      projectName?.let { true to it } ?: (false to module.name)
    }
    else true to module.name

    val intentionName = PyCharmCommunityCustomizationBundle.message("sdk.set.up.uv.environment", projectName)
    val envNotFound = EnvCheckerResult.EnvNotFound(intentionName)

    return when {
      checkExistence -> {
        val detectedSdk = getUvEnv(if (checkToml) module else module.getSdkAssociatedModule())
        detectedSdk?.findEnvOrNull(intentionName) ?: if (canManage) envNotFound else EnvCheckerResult.CannotConfigure
      }
      canManage -> envNotFound
      else -> EnvCheckerResult.CannotConfigure
    }
  }

  private fun getUvEnv(module: Module): PyDetectedSdk? = detectAssociatedEnvironments(module, existingSdks, context).firstOrNull {
    it.pyvenvContains("uv = ")
  }

  private suspend fun Module.getSdkAssociatedModule() =
    when (val r = suggestSdk()) {
      // Workspace suggested by uv
      is SuggestedSdk.SameAs -> if (r.accordingTo == toolId) r.parentModule else null
      null, is SuggestedSdk.PyProjectIndependent -> null
    } ?: this

  private suspend fun createUv(module: Module, envExists: Boolean): PyResult<Sdk> {
    val sdkAssociatedModule = module.getSdkAssociatedModule()
    val workingDir: Path? = tryResolvePath(sdkAssociatedModule.basePath)
    if (workingDir == null) {
      throw IllegalStateException("Can't determine working dir for the module")
    }

    val sdkSetupResult = if (envExists) {
      getUvEnv(sdkAssociatedModule)?.homePath?.toNioPathOrNull()?.let {
        setupExistingEnvAndSdk(it, workingDir, false, workingDir, existingSdks)
      } ?: run {
        logger.warn("Can't find existing uv environment in project, but it was expected. " +
                    "Probably it was deleted. New environment will be created")
        setupNewUvSdkAndEnv(workingDir, existingSdks, null)
      }
    }
    else setupNewUvSdkAndEnv(workingDir, existingSdks, null)

    sdkSetupResult.onSuccess {
      withContext(Dispatchers.EDT) {
        it.persist()
        it.setAssociationToModule(sdkAssociatedModule)
      }
    }
    return sdkSetupResult
  }
}
