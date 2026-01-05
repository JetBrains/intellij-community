package com.intellij.python.pyproject.model.api

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.guessModuleDir
import com.intellij.python.common.tools.ToolId
import com.intellij.python.pyproject.model.internal.suggestSdkImpl
import com.jetbrains.python.sdk.configuration.CreateSdkInfo
import com.jetbrains.python.sdk.configuration.CreateSdkInfoWithTool
import com.jetbrains.python.sdk.configuration.PyProjectSdkConfigurationExtension
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
): ModuleCreateInfo? = // Save on module level
  when (val r = suggestSdk()) {
    is SuggestedSdk.PyProjectIndependent -> {
      val tools = r.preferTools.map { configuratorsByTool[it]!! }
      tools.firstNotNullOfOrNull { tool ->
        val createInfo = (tool.asPyProjectTomlSdkConfigurationExtension()?.createSdkWithoutPyProjectTomlChecks(this)
                          ?: tool.checkEnvironmentAndPrepareSdkCreator(this)) ?: return@firstNotNullOfOrNull null
        CreateSdkInfoWithTool(createInfo, tool.toolId).asDTO(r.moduleDir)
      }
    }
    is SuggestedSdk.SameAs -> {
      ModuleCreateInfo.SameAs(r.parentModule)
    }
    null -> null
  } // No tools or not pyproject.toml at all? Use EP as a fallback
  ?: PyProjectSdkConfigurationExtension.findAllSortedForModule(this).firstOrNull()
    ?.let { CreateSdkInfoWithTool(it.createSdkInfo, it.toolId).asDTO(guessModuleDir()?.toNioPath()) }

sealed interface ModuleCreateInfo {
  data class CreateSdkInfoWrapper(val createSdkInfo: CreateSdkInfo, val toolId: ToolId, val moduleDir: Directory?) : ModuleCreateInfo
  data class SameAs(val parentModule: Module) : ModuleCreateInfo
}

private fun CreateSdkInfoWithTool.asDTO(moduleDir: Directory?): ModuleCreateInfo =
  ModuleCreateInfo.CreateSdkInfoWrapper(createSdkInfo, toolId, moduleDir)
