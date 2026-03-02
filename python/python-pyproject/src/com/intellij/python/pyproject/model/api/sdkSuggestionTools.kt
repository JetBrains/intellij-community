package com.intellij.python.pyproject.model.api

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.guessModuleDir
import com.intellij.python.common.tools.ToolId
import com.intellij.python.pyproject.model.internal.suggestSdkImpl
import com.jetbrains.python.sdk.configuration.CreateSdkInfo
import com.jetbrains.python.sdk.configuration.CreateSdkInfoWithTool
import com.jetbrains.python.sdk.configuration.PyProjectSdkConfigurationExtension
import com.jetbrains.python.sdk.configuration.findPythonVirtualEnvironments
import com.jetbrains.python.venvReader.Directory


sealed interface SuggestedSdk {
  /**
   * Part of workspace with [parentModule] as a root
   */
  data class SameAs(val parentModule: Module, val accordingTo: ToolId) : SuggestedSdk

  /**
   * Standalone module. When possible, use one of [preferTools] to configure it.
   * Module's toml file is in [moduleDir]
   */
  data class PyProjectIndependent(val preferTools: Set<ToolId>, val moduleDir: Directory) : SuggestedSdk
}


/**
 * Suggests how to configure SDK for a certain module.
 * `null` means this module is not `pyproject.toml` based
 */
suspend fun Module.suggestSdk(): SuggestedSdk? = suggestSdkImpl(this)


/**
 * For multiple calls, pull [configuratorsByTool] up not to create it each time
 */
suspend fun Module.getModuleInfo(
  configuratorsByTool: Map<ToolId, PyProjectSdkConfigurationExtension> = PyProjectSdkConfigurationExtension.createMap(),
): ModuleCreateInfo? { // Save on module level
  val venvsInModule = findPythonVirtualEnvironments()
  val bestProposalFromTools = PyProjectSdkConfigurationExtension.findAllSortedForModule(this, venvsInModule).firstOrNull()

  val suggestedByPyProjectToml = when (val suggestedSdk = suggestSdk()) {
    is SuggestedSdk.PyProjectIndependent -> {
      when (bestProposalFromTools?.createSdkInfo) {
        is CreateSdkInfo.ExistingEnv -> bestProposalFromTools.asDTO(suggestedSdk.moduleDir)
        is CreateSdkInfo.WillCreateEnv, is CreateSdkInfo.WillInstallTool, null -> {
          configuratorsByTool
            .filter { it.key in suggestedSdk.preferTools }
            .firstNotNullOfOrNull { (toolId, extension) ->
              extension.asPyProjectTomlSdkConfigurationExtension()?.createSdkWithoutPyProjectTomlChecks(this, venvsInModule)?.let {
                CreateSdkInfoWithTool(it, toolId).asDTO(suggestedSdk.moduleDir)
              }
            }
        }
      }
    }
    is SuggestedSdk.SameAs -> {
      ModuleCreateInfo.SameAs(suggestedSdk.parentModule)
    }
    null -> null
  }
  suggestedByPyProjectToml?.let { return it }

  // No tools or not pyproject.toml at all? Use EP as a fallback
  return bestProposalFromTools?.let {
    CreateSdkInfoWithTool(it.createSdkInfo, it.toolId).asDTO(guessModuleDir()?.toNioPath())
  }
}

sealed interface ModuleCreateInfo {
  data class CreateSdkInfoWrapper(val createSdkInfo: CreateSdkInfo, val toolId: ToolId, val moduleDir: Directory?) : ModuleCreateInfo
  data class SameAs(val parentModule: Module) : ModuleCreateInfo
}

private fun CreateSdkInfoWithTool.asDTO(moduleDir: Directory?): ModuleCreateInfo =
  ModuleCreateInfo.CreateSdkInfoWrapper(createSdkInfo, toolId, moduleDir)
