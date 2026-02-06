// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.venv.sdk.configuration

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.python.common.tools.ToolId
import com.intellij.python.venv.PY_REQ_TOOL_ID
import com.jetbrains.python.PyBundle
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.packaging.PyPackageUtil
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.packaging.requirementsTxt.PythonRequirementTxtSdkUtils
import com.jetbrains.python.projectCreation.createVenvAndSdk
import com.jetbrains.python.sdk.ModuleOrProject
import com.jetbrains.python.sdk.configuration.CreateSdkInfo
import com.jetbrains.python.sdk.configuration.EnvCheckerResult
import com.jetbrains.python.sdk.configuration.PyProjectSdkConfigurationExtension
import com.jetbrains.python.sdk.configuration.PyProjectTomlConfigurationExtension
import com.jetbrains.python.sdk.configuration.PySdkConfigurationCollector
import com.jetbrains.python.sdk.configuration.PySdkConfigurationCollector.VirtualEnvResult
import com.jetbrains.python.sdk.configuration.prepareSdkCreator
import com.jetbrains.python.packaging.setupPy.SetupPyHelpers.SETUP_PY
import com.jetbrains.python.sdk.configuration.*
import com.intellij.openapi.application.readAction

internal val PY_REQ_TOOL_ID = ToolId("requirements.txt")

internal class PyRequirementsTxtOrSetupPySdkConfiguration : PyProjectSdkConfigurationExtension {

  override val toolId: ToolId = PY_REQ_TOOL_ID // This is nonsense, but will be dropped soon

  override suspend fun checkEnvironmentAndPrepareSdkCreator(module: Module, venvsInModule: List<PythonBinary>): CreateSdkInfo? =
    prepareSdkCreator(
      { checkManageableEnv(module) },
    ) { { createAndAddSdk(module) } }

  override fun asPyProjectTomlSdkConfigurationExtension(): PyProjectTomlConfigurationExtension? = null

  private suspend fun checkManageableEnv(module: Module): EnvCheckerResult {
    val configFile = readAction { getRequirementsTxtOrSetupPy(module) } ?: return EnvCheckerResult.CannotConfigure
    return EnvCheckerResult.EnvNotFound(PyBundle.message("sdk.create.venv.suggestion", configFile.name))
  }

  private suspend fun createAndAddSdk(module: Module): PyResult<Sdk> {
    val sdk = createVenvAndSdk(ModuleOrProject.ModuleAndProject(module)).getOr { return it }
    PySdkConfigurationCollector.logVirtualEnv(module.project, VirtualEnvResult.CREATED)

    val requirementsTxtOrSetupPyFile = readAction { getRequirementsTxtOrSetupPy(module) }
    if (requirementsTxtOrSetupPyFile == null) {
      PySdkConfigurationCollector.logVirtualEnv(module.project, VirtualEnvResult.DEPS_NOT_FOUND)
      thisLogger().warn("File with dependencies is not found")
      return PyResult.success(sdk)
    }

    val isRequirements = requirementsTxtOrSetupPyFile.name != SETUP_PY

    if (isRequirements) {
      PythonRequirementTxtSdkUtils.saveRequirementsTxtPath(module.project, sdk, requirementsTxtOrSetupPyFile.toNioPath())
    }

    return PythonPackageManager.forSdk(module.project, sdk).sync().mapSuccess { sdk }
  }

  private fun getRequirementsTxtOrSetupPy(module: Module) =
    PyPackageUtil.findRequirementsTxt(module) ?: PyPackageUtil.findSetupPy(module)?.virtualFile
}
