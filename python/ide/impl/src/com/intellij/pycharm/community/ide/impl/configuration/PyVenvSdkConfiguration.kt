// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.configuration

import com.intellij.codeInspection.util.IntentionName
import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.pycharm.community.ide.impl.PyCharmCommunityCustomizationBundle
import com.jetbrains.python.errorProcessing.MessageError
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.sdk.*
import com.jetbrains.python.sdk.configuration.PyProjectSdkConfigurationExtension
import com.jetbrains.python.sdk.legacy.PythonSdkUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class PyVenvSdkConfiguration : PyProjectSdkConfigurationExtension {
  private val existingSdks = PythonSdkUtil.getAllSdks()
  private val context = UserDataHolderBase()

  override suspend fun getIntention(module: Module): @IntentionName String? =
    withContext(Dispatchers.IO) {
      detectAssociatedEnvironments(module, existingSdks, context).firstOrNull()
    }?.let {
      PyCharmCommunityCustomizationBundle.message("sdk.create.venv.suggestion", it.name)
    }

  override suspend fun createAndAddSdkForConfigurator(module: Module): PyResult<Sdk> = setupVenv(module)

  override suspend fun createAndAddSdkForInspection(module: Module): PyResult<Sdk> = setupVenv(module)

  private suspend fun setupVenv(module: Module): PyResult<Sdk> {
    val env = withContext(Dispatchers.IO) {
      detectAssociatedEnvironments(module, existingSdks, context).firstOrNull()
    } ?: return PyResult.failure(MessageError("Can't find venv for the module"))

    val sdk = env.setupAssociated(existingSdks, module.basePath, true).getOr { return it }
    sdk.persist()

    return PyResult.success(sdk)
  }
}