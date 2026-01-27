// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.configuration

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.pycharm.community.ide.impl.PyCharmCommunityCustomizationBundle
import com.intellij.pycharm.community.ide.impl.configuration.PySdkConfigurationCollector.VirtualEnvResult
import com.intellij.python.common.tools.ToolId
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.packaging.PyPackageUtil
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.packaging.requirementsTxt.PythonRequirementTxtSdkUtils
import com.jetbrains.python.packaging.setupPy.SetupPyManager
import com.jetbrains.python.projectCreation.createVenvAndSdk
import com.jetbrains.python.sdk.ModuleOrProject
import com.jetbrains.python.sdk.configuration.*

internal val PY_REQ_TOOL_ID = ToolId("requirements.txt")

internal class PyRequirementsTxtOrSetupPySdkConfiguration : PyProjectSdkConfigurationExtension {

  override val toolId: ToolId = PY_REQ_TOOL_ID // This is nonsense, but will be dropped soon

  override suspend fun checkEnvironmentAndPrepareSdkCreator(module: Module, venvsInModule: List<PythonBinary>): CreateSdkInfo? = prepareSdkCreator(
    { checkManageableEnv(module) },
  ) { { createAndAddSdk(module) } }

  override fun asPyProjectTomlSdkConfigurationExtension(): PyProjectTomlConfigurationExtension? = null

  private fun checkManageableEnv(module: Module): EnvCheckerResult {
    val configFile = getRequirementsTxtOrSetupPy(module) ?: return EnvCheckerResult.CannotConfigure
    return EnvCheckerResult.EnvNotFound(PyCharmCommunityCustomizationBundle.message("sdk.create.venv.suggestion", configFile.name))
  }

  private suspend fun createAndAddSdk(module: Module): PyResult<Sdk> {
    val sdk = createVenvAndSdk(ModuleOrProject.ModuleAndProject(module)).getOr { return it }
    PySdkConfigurationCollector.logVirtualEnv(module.project, VirtualEnvResult.CREATED)

    val requirementsTxtOrSetupPyFile = getRequirementsTxtOrSetupPy(module)
    if (requirementsTxtOrSetupPyFile == null) {
      PySdkConfigurationCollector.logVirtualEnv(module.project, VirtualEnvResult.DEPS_NOT_FOUND)
      thisLogger().warn("File with dependencies is not found")
      return PyResult.success(sdk)
    }

    val isRequirements = requirementsTxtOrSetupPyFile.name != SetupPyManager.SETUP_PY

    if (isRequirements) {
      PythonRequirementTxtSdkUtils.saveRequirementsTxtPath(module.project, sdk, requirementsTxtOrSetupPyFile.toNioPath())
    }

    return PythonPackageManager.forSdk(module.project, sdk).sync().mapSuccess { sdk }
  }

  private fun getRequirementsTxtOrSetupPy(module: Module) =
    PyPackageUtil.findRequirementsTxt(module) ?: PyPackageUtil.findSetupPy(module)?.virtualFile

}
