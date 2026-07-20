// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.configuration

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.Disposer
import com.intellij.python.community.common.tools.ToolId
import com.intellij.python.pyproject.model.api.SuggestedSdk
import com.intellij.python.pyproject.model.api.suggestSdk
import com.jetbrains.python.PythonPluginDisposable
import com.jetbrains.python.errorProcessing.ErrorSink
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.errorProcessing.emit
import com.jetbrains.python.sdk.configuration.suppressors.PyPackageRequirementsInspectionSuppressor
import com.jetbrains.python.sdk.configuration.suppressors.TipOfTheDaySuppressor
import com.jetbrains.python.sdk.configurePythonSdk
import com.jetbrains.python.sdk.impl.PySdkBundle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object PyProjectSdkConfiguration {
  suspend fun setSdkUsingCreateSdkInfo(
    module: Module, createSdkInfoWithTool: CreateSdkInfoWithTool,
  ): Boolean = withContext(Dispatchers.Default) {
    thisLogger().debug("Configuring sdk using ${createSdkInfoWithTool.toolId}")

    val sdk = when (val createSdkInfo = createSdkInfoWithTool.createSdkInfo) {
      is CreateSdkInfo.WillInstallTool ->
        // This specific CreateSdkInfo is only supposed to be used for proposing tool installation,
        // it never should be used for SDK creation.
        PyResult.localizedError(PySdkBundle.message("python.sdk.cannot.create.tool.should.be.installed"))
      is CreateSdkInfo.ExistingEnv, is CreateSdkInfo.WillCreateEnv -> createSdkInfo.getSdkCreator(module).createSdk()
    }.getOr {
      ErrorSink().emit(it.error, module.project)
      return@withContext true
    }

    module.getRootModuleOrNull(createSdkInfoWithTool.toolId)?.also { configurePythonSdk(it.project, it, sdk) }
    configurePythonSdk(module.project, module, sdk)
    thisLogger().debug("Successfully configured sdk using ${createSdkInfoWithTool.toolId}")
    true
  }

  fun suppressTipAndInspectionsFor(module: Module, toolName: String): Disposable {
    val project = module.project

    val lifetime = Disposer.newDisposable(
      PythonPluginDisposable.getInstance(project),
      "Configuring sdk using $toolName"
    )

    TipOfTheDaySuppressor.suppress()?.let { Disposer.register(lifetime, it) }
    Disposer.register(lifetime, PyPackageRequirementsInspectionSuppressor(module))

    PythonSdkCreationWaiter.register(module, lifetime)
    return lifetime
  }
}

private suspend fun Module.getRootModuleOrNull(toolId: ToolId): Module? =
  when (val r = suggestSdk()) {
    // Workspace suggested by uv
    is SuggestedSdk.SameAs -> if (r.accordingTo == toolId) r.parentModule else null
    null, is SuggestedSdk.PyProjectIndependent -> null
  }

internal suspend fun Module.getSdkAssociatedModule(toolId: ToolId): Module = getRootModuleOrNull(toolId) ?: this
