package com.intellij.python.pyproject.model.internal

import com.intellij.openapi.module.Module
import com.intellij.python.community.common.tools.ToolId
import com.intellij.python.pyproject.model.api.ModuleSdkState
import com.intellij.python.pyproject.model.api.SdkForModuleConfigInstruction
import com.jetbrains.python.sdk.configuration.CreateSdkInfo
import com.jetbrains.python.sdk.configuration.CreateSdkInfoWithTool
import com.jetbrains.python.sdk.configuration.ModuleConfigurators
import com.jetbrains.python.sdk.configuration.PyProjectSdkConfigurationExtension
import com.jetbrains.python.sdk.configuration.findPythonVirtualEnvironments
import com.jetbrains.python.sdk.findPythonSdk

/**
 * See usage for an API doc
 */
internal suspend fun Module.getModuleSdkStateImpl(
  configuratorsByTool: Map<ToolId, PyProjectSdkConfigurationExtension> = PyProjectSdkConfigurationExtension.createMap(),
  fresh: Boolean = false,
): ModuleSdkState {
  val currentSdk = findPythonSdk()
  return if (currentSdk != null) {
    ModuleSdkState.HasSdk(currentSdk)
  }
  else {
    val suggestedByPyProjectToml = when (val suggestedSdk = suggestSdk()) {
      is SuggestedSdk.PyProjectIndependent, null -> {
        // Both halves come from one probe, cached unless the caller asked for a live answer: asking the configurators
        // runs their tools, and this is the busiest way into them.
        val configurators = if (fresh) {
          val venvs = findPythonVirtualEnvironments()
          ModuleConfigurators(venvs, PyProjectSdkConfigurationExtension.findAllSortedForModule(this, venvs))
        }
        else PyProjectSdkConfigurationExtension.findAllSortedForModuleCached(this)
        val venvsInModule = configurators.venvsInModule
        val bestProposalFromTools = configurators.options.firstOrNull()
        when (bestProposalFromTools?.createSdkInfo) {
          is CreateSdkInfo.ExistingEnv -> bestProposalFromTools
          is CreateSdkInfo.WillCreateEnv, is CreateSdkInfo.WillInstallTool, null -> {
            suggestedSdk?.let { suggestedSdk->
              configuratorsByTool
                // First, find suggested tool that is also proposed by the fact of its venv existence
                .filter { it.key in suggestedSdk.preferTools }
                .firstNotNullOfOrNull { (toolId, extension) ->
                  extension.asPyProjectTomlSdkConfigurationExtension()?.createSdkWithoutPyProjectTomlChecks(this, venvsInModule)?.let {
                    CreateSdkInfoWithTool(it, toolId)
                  }
                }
            } ?: bestProposalFromTools
            // No tools or not pyproject.toml at all? Use EP as a fallback
          }
        }?.let { SdkForModuleConfigInstruction.CreateSdkInfoWrapper(this, it) }
      }
      is SuggestedSdk.SameAs -> {
        SdkForModuleConfigInstruction.SameAs(this, suggestedSdk.parentModule, suggestedSdk.accordingTo)
      }
    }
    ModuleSdkState.NoSdk(suggestedByPyProjectToml)
  }
}
