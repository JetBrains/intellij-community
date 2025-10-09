// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.configuration

import com.intellij.codeInspection.util.IntentionName
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.vfs.readText
import com.intellij.pycharm.community.ide.impl.PyCharmCommunityCustomizationBundle
import com.intellij.python.pyproject.PyProjectToml
import com.intellij.python.pyproject.model.api.SuggestedSdk
import com.intellij.python.pyproject.model.api.suggestSdk
import com.jetbrains.python.ToolId
import com.jetbrains.python.errorProcessing.MessageError
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.getOrLogException
import com.jetbrains.python.onSuccess
import com.jetbrains.python.projectModel.uv.UV_TOOL_ID
import com.jetbrains.python.sdk.legacy.PythonSdkUtil
import com.jetbrains.python.sdk.basePath
import com.jetbrains.python.sdk.configuration.PyProjectSdkConfigurationExtension
import com.jetbrains.python.sdk.persist
import com.jetbrains.python.sdk.setAssociationToModule
import com.jetbrains.python.sdk.uv.impl.getUvExecutable
import com.jetbrains.python.sdk.uv.setupNewUvSdkAndEnv
import com.jetbrains.python.venvReader.tryResolvePath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.io.IOException
import java.nio.file.Path

private val logger = fileLogger()

@ApiStatus.Internal
class PyUvSdkConfiguration : PyProjectSdkConfigurationExtension {
  override val toolId: ToolId = UV_TOOL_ID

  override suspend fun getIntention(module: Module): @IntentionName String? {
    val tomlFile = PyProjectToml.findFile(module) ?: return null
    getUvExecutable() ?: return null

    val tomlFileContent = withContext(Dispatchers.IO) {
      try {
        tomlFile.readText()
      }
      catch (e: IOException) {
        logger.debug("Can't read ${tomlFile}", e)
        null
      }
    } ?: return null
    val tomlContentResult = withContext(Dispatchers.Default) { PyProjectToml.parse(tomlFileContent) }
    val tomlContent = tomlContentResult.getOrLogException(logger) ?: return null
    val project = tomlContent.project ?: return null


    return PyCharmCommunityCustomizationBundle.message("sdk.set.up.uv.environment", project.name ?: tomlFile.inputStream)
  }

  override suspend fun createAndAddSdkForConfigurator(module: Module): PyResult<Sdk> = createUv(module)

  override suspend fun createAndAddSdkForInspection(module: Module): PyResult<Sdk> = createUv(module)

  override fun supportsHeadlessModel(): Boolean = true

  private suspend fun createUv(module: Module): PyResult<Sdk> {
    val sdkAssociatedModule =
      when (val r = module.suggestSdk()) {
        // Workspace suggested by uv
        is SuggestedSdk.SameAs -> if (r.accordingTo == toolId) r.parentModule else null
        null, is SuggestedSdk.PyProjectIndependent -> null
      } ?: module


    val workingDir: Path? = tryResolvePath(sdkAssociatedModule.basePath)
    if (workingDir == null) {
      return PyResult.failure(MessageError("Can't determine working dir for the module"))
    }

    val sdkSetupResult = setupNewUvSdkAndEnv(workingDir, PythonSdkUtil.getAllSdks(), null)
    sdkSetupResult.onSuccess {
      withContext(Dispatchers.EDT) {
        it.persist()
        it.setAssociationToModule(sdkAssociatedModule)
      }
    }
    return sdkSetupResult
  }
}