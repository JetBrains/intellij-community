// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.venv.sdk.configuration

import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.module.Module
import com.intellij.python.community.common.tools.ToolId
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.python.sdk.backend.getSdkAPI
import com.intellij.python.venv.PY_REQ_TOOL_ID
import com.jetbrains.python.PyBundle
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.packaging.PyPackageUtil
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.packaging.requirementsTxt.PythonRequirementTxtSdkUtils
import com.jetbrains.python.packaging.setupPy.SetupPyHelpers.SETUP_PY
import com.jetbrains.python.project.PyProject
import com.jetbrains.python.project.project
import com.jetbrains.python.projectCreation.createVenvAndSdk
import com.jetbrains.python.sdk.ModuleOrProject
import com.jetbrains.python.sdk.PythonSdkAdditionalData
import com.jetbrains.python.sdk.configuration.CreateInterpreterInfo
import com.jetbrains.python.sdk.configuration.EnvCheckerResult
import com.jetbrains.python.sdk.configuration.PyProjectSdkConfigurationExtension
import com.jetbrains.python.sdk.configuration.PyProjectTomlConfigurationExtension
import com.jetbrains.python.sdk.configuration.PySdkConfigurationCollector
import com.jetbrains.python.sdk.configuration.PySdkConfigurationCollector.VirtualEnvResult
import com.jetbrains.python.sdk.configuration.prepareSdkCreator

internal class PyRequirementsTxtOrSetupPySdkConfiguration : PyProjectSdkConfigurationExtension {

  override val toolId: ToolId = PY_REQ_TOOL_ID // This is nonsense, but will be dropped soon

  override val potentialDependencyFiles: Set<String> = setOf(PythonSdkAdditionalData.REQUIREMENT_TXT_DEFAULT.fileName.toString(), SETUP_PY)

  override suspend fun checkEnvironmentAndPrepareSdkCreator(pyProject: PyProject, venvs: List<PythonBinary>): CreateInterpreterInfo? =
    prepareSdkCreator(
      { checkManageableEnv(pyProject) },
    ) { { createAndAddSdk(pyProject) } }

  override fun asPyProjectTomlSdkConfigurationExtension(): PyProjectTomlConfigurationExtension? = null

  private suspend fun checkManageableEnv(pyProject: PyProject): EnvCheckerResult {
    val configFile = readAction { getRequirementsTxtOrSetupPy(pyProject.residesOnModule) } ?: return EnvCheckerResult.CannotConfigure
    return EnvCheckerResult.EnvNotFound(PyBundle.message("sdk.create.venv.suggestion", configFile.name))
  }

  private suspend fun createAndAddSdk(pyProject: PyProject): PyResult<PythonInterpreter> {
    val project = pyProject.project
    val pythonInterpreter = createVenvAndSdk(ModuleOrProject.ModuleAndProject(pyProject)).getOr { return it }
    PySdkConfigurationCollector.logVirtualEnv(project, VirtualEnvResult.CREATED)

    val requirementsTxtOrSetupPyFile = readAction { getRequirementsTxtOrSetupPy(pyProject.residesOnModule) }
    if (requirementsTxtOrSetupPyFile == null) {
      PySdkConfigurationCollector.logVirtualEnv(project, VirtualEnvResult.DEPS_NOT_FOUND)
      thisLogger().warn("File with dependencies is not found")
      return PyResult.success(pythonInterpreter)
    }

    val isRequirements = requirementsTxtOrSetupPyFile.name != SETUP_PY

    if (isRequirements) {
      PythonRequirementTxtSdkUtils.saveRequirementsTxtPath(project, pythonInterpreter.getSdkAPI(), requirementsTxtOrSetupPyFile.toNioPath())
    }

    return PythonPackageManager.forPythonInterpreter(project, pythonInterpreter).syncLocked().mapSuccess { pythonInterpreter }
  }

  // Reads the module and not the project: both finders walk the module's source roots, which a base dir does not state.
  private fun getRequirementsTxtOrSetupPy(module: Module) =
    PyPackageUtil.findRequirementsTxt(module) ?: PyPackageUtil.findSetupPy(module)?.virtualFile
}
