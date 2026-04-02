// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.configuration

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.Disposer
import com.intellij.python.common.tools.ToolId
import com.intellij.python.community.services.systemPython.SystemPythonService
import com.intellij.python.pyproject.model.api.SuggestedSdk
import com.intellij.python.pyproject.model.api.suggestSdk
import com.jetbrains.python.PyBundle
import com.jetbrains.python.PythonPluginDisposable
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.errorProcessing.emit
import com.jetbrains.python.sdk.configuration.suppressors.PyPackageRequirementsInspectionSuppressor
import com.jetbrains.python.sdk.configuration.suppressors.TipOfTheDaySuppressor
import com.jetbrains.python.sdk.configurePythonSdk
import com.jetbrains.python.sdk.installExecutableViaPythonScript
import com.jetbrains.python.util.ShowingMessageErrorSync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

object PyProjectSdkConfiguration {
  internal suspend fun installToolAndShowErrorIfNeeded(module: Module, pathPersister: (Path) -> Unit, toolToInstall: String) {
    performToolInstallation(pathPersister, toolToInstall).errorOrNull?.also {
      ShowingMessageErrorSync.emit(it, module.project)
    }
  }

  private suspend fun performToolInstallation(pathPersister: (Path) -> Unit, toolToInstall: String): PyResult<Unit> {
    val systemPython = SystemPythonService().findSystemPythons().firstOrNull()
                       ?: return PyResult.localizedError(PyBundle.message("sdk.cannot.find.python"))
    return installExecutableViaPythonScript(systemPython.asExecutablePython.binary, "-n", toolToInstall).mapSuccess(pathPersister)
  }

  suspend fun setSdkUsingCreateSdkInfo(
    module: Module, createSdkInfoWithTool: CreateSdkInfoWithTool,
  ): Boolean = withContext(Dispatchers.Default) {
    thisLogger().debug("Configuring sdk using ${createSdkInfoWithTool.toolId}")

    val sdk = createSdkInfoWithTool.createSdkInfo.getSdkCreator(module).createSdk().getOr {
      ShowingMessageErrorSync.emit(it.error, module.project)
      return@withContext true
    }

    module.getRootModuleOrNull(createSdkInfoWithTool.toolId)?.also { configurePythonSdk(it.project, it, sdk) }
    configurePythonSdk(module.project, module, sdk)
    thisLogger().debug("Successfully configured sdk using ${createSdkInfoWithTool.toolId}")
    true
  }

  @ApiStatus.Obsolete
  fun setReadyToUseSdk(project: Project, module: Module, sdk: Sdk) {
    if (module.isDisposed) {
      return
    }

    configurePythonSdk(project, module, sdk)
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
