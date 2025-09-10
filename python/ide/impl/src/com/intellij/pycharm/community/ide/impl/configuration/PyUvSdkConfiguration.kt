// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.configuration

import com.intellij.codeInspection.util.IntentionName
import com.intellij.openapi.application.EDT
import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.registry.Registry
import com.intellij.pycharm.community.ide.impl.PyCharmCommunityCustomizationBundle
import com.intellij.python.pyproject.PyProjectToml
import com.jetbrains.python.errorProcessing.MessageError
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.onSuccess
import com.jetbrains.python.projectModel.uv.UvProjectModelService
import com.jetbrains.python.sdk.*
import com.jetbrains.python.sdk.configuration.PyProjectSdkConfigurationExtension
import com.jetbrains.python.sdk.uv.impl.getUvExecutable
import com.jetbrains.python.sdk.uv.setupNewUvSdkAndEnv
import com.jetbrains.python.venvReader.tryResolvePath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

@ApiStatus.Internal
class PyUvSdkConfiguration : PyProjectSdkConfigurationExtension {
  override suspend fun getIntention(module: Module): @IntentionName String? {
    return PyProjectToml.findFile(module)?.let { toml ->
      getUvExecutable()?.let { PyCharmCommunityCustomizationBundle.message("sdk.set.up.uv.environment", toml.name) }
    }
  }

  override suspend fun createAndAddSdkForConfigurator(module: Module): PyResult<Sdk> = createUv(module)

  override suspend fun createAndAddSdkForInspection(module: Module): PyResult<Sdk> = createUv(module)

  override fun supportsHeadlessModel(): Boolean = true

  private suspend fun createUv(module: Module): PyResult<Sdk> {
    val sdkAssociatedModule: Module
    if (Registry.`is`("python.project.model.uv", false)) {
      val uvWorkspace = UvProjectModelService.findWorkspace(module)
      sdkAssociatedModule = uvWorkspace?.root ?: module
    }
    else {
      sdkAssociatedModule = module
    }
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