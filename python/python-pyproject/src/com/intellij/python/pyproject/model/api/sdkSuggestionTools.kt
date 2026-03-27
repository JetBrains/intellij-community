package com.intellij.python.pyproject.model.api

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.guessModuleDir
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.python.common.tools.ToolId
import com.intellij.python.pyproject.model.internal.suggestSdkImpl
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.onSuccess
import com.jetbrains.python.sdk.configuration.CreateSdkInfo
import com.jetbrains.python.sdk.configuration.CreateSdkInfoWithTool
import com.jetbrains.python.sdk.configuration.PyProjectSdkConfigurationExtension
import com.jetbrains.python.sdk.configuration.findPythonVirtualEnvironments
import com.jetbrains.python.sdk.configuration.getSdkCreator
import com.jetbrains.python.sdk.findPythonSdk
import com.jetbrains.python.sdk.pythonSdk
import com.jetbrains.python.sdk.setAssociationToModule
import com.jetbrains.python.sdk.withSdkConfigurationLock
import com.jetbrains.python.venvReader.Directory
import org.jetbrains.annotations.ApiStatus


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
 * Suggests an [ModuleCreateInfo] for this module, or returns `null` if the module
 * already has a Python SDK or is not a Python module.
 *
 * Suspends until the project model is fully loaded (via [findPythonSdk]) before checking,
 * so it is safe to call during startup without risking a false positive from a stale SDK table.
 *
 * For multiple calls, pull [configuratorsByTool] up not to create it each time.
 */
suspend fun Module.getModuleInfo(
  configuratorsByTool: Map<ToolId, PyProjectSdkConfigurationExtension> = PyProjectSdkConfigurationExtension.createMap(),
): ModuleCreateInfo? { // Save on module level
  findPythonSdk()?.let { return null }

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


/**
 * Auto-configures a Python SDK for the module if one doesn't already exist.
 *
 * Waits for the SDK table to load (to avoid overwriting a persisted SDK that hasn't resolved yet),
 * then detects the best SDK using [getModuleInfo] and assigns it to the module.
 *
 * Returns the configured SDK, or `null` if no SDK could be configured.
 */
@ApiStatus.Internal
suspend fun Module.autoConfigureSdkIfNeeded(): PyResult<Sdk>? = withSdkConfigurationLock(project) {
  val module = this@autoConfigureSdkIfNeeded

  when (val moduleInfo = module.getModuleInfo()) {
    is ModuleCreateInfo.CreateSdkInfoWrapper -> {
      when (moduleInfo.createSdkInfo) {
        is CreateSdkInfo.ExistingEnv -> {
          moduleInfo.createSdkInfo.getSdkCreator(module).createSdk().onSuccess { sdk ->
            module.pythonSdk = sdk
            sdk.setAssociationToModule(module)
          }
        }
        is CreateSdkInfo.WillCreateEnv, is CreateSdkInfo.WillInstallTool -> null
      }
    }
    is ModuleCreateInfo.SameAs -> {
      moduleInfo.parentModule.findPythonSdk()?.let { parentSdk ->
        module.pythonSdk = parentSdk
        PyResult.success(parentSdk)
      }
    }
    null -> null
  }
}
