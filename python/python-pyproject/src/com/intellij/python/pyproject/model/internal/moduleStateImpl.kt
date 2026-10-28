package com.intellij.python.pyproject.model.internal

import com.intellij.python.community.common.tools.ToolId
import com.intellij.python.pyproject.model.api.ModuleSdkState
import com.intellij.python.pyproject.model.api.SdkForModuleConfigInstruction
import com.intellij.python.pyproject.model.evolution.readStoredInterpreter
import com.jetbrains.python.project.PyProject
import com.jetbrains.python.project.PyProject.Companion.asPyProject
import com.jetbrains.python.sdk.configuration.CreateInterpreterInfo
import com.jetbrains.python.sdk.configuration.CreateSdkInfoWithTool
import com.jetbrains.python.sdk.configuration.PyProjectConfigurators
import com.jetbrains.python.sdk.configuration.PyProjectSdkConfigurationExtension
import com.jetbrains.python.sdk.configuration.findPythonVirtualEnvironments

/**
 * See usage for an API doc
 */
internal suspend fun PyProject.getModuleSdkStateImpl(
  configuratorsByTool: Map<ToolId, PyProjectSdkConfigurationExtension> = PyProjectSdkConfigurationExtension.createMap(),
  fresh: Boolean = false,
): ModuleSdkState {
  // The stored entry decides, as the configuration lock needs a true answer at this instant. An entry whose interpreter
  // the registry does not hold counts as no interpreter, so it can be configured again.
  val interpreter = residesOnModule.readStoredInterpreter()
  if (interpreter != null) return ModuleSdkState.HasSdk(interpreter)
  return ModuleSdkState.NoSdk(suggestConfigInstruction(configuratorsByTool, fresh))
}

private suspend fun PyProject.suggestConfigInstruction(
  configuratorsByTool: Map<ToolId, PyProjectSdkConfigurationExtension>,
  fresh: Boolean,
): SdkForModuleConfigInstruction? =
  when (val suggestedSdk = residesOnModule.suggestSdk()) {
    is SuggestedSdk.PyProjectIndependent, null -> {
      // Both halves come from one probe, cached unless the caller asked for a live answer: asking the configurators
      // runs their tools, and this is the busiest way into them.
      val configurators = if (fresh) {
        val venvs = findPythonVirtualEnvironments()
        PyProjectConfigurators(venvs, PyProjectSdkConfigurationExtension.findAllSorted(this, venvs))
      }
      else PyProjectSdkConfigurationExtension.findAllSortedCached(this)
      val venvs = configurators.venvs
      val bestProposalFromTools = configurators.options.firstOrNull()
      when (bestProposalFromTools?.createSdkInfo) {
        is CreateInterpreterInfo.ExistingEnv -> bestProposalFromTools
        is CreateInterpreterInfo.WillCreateEnv, is CreateInterpreterInfo.WillInstallTool, null -> {
          suggestedSdk?.let { suggestedSdk ->
            val answeredByTool = configurators.options.associateBy { it.toolId }
            configuratorsByTool
              // First, find suggested tool that is also proposed by the fact of its venv existence
              .filter { it.key in suggestedSdk.preferTools }
              .firstNotNullOfOrNull { (toolId, extension) ->
                // The probe above already asked this configurator, and its other entry point differs only by the
                // pyproject.toml precondition that answer implies. Reuse it instead of running the tool twice.
                answeredByTool[toolId]
                ?: extension.asPyProjectTomlSdkConfigurationExtension()?.createSdkWithoutPyProjectTomlChecks(this, venvs)?.let {
                  CreateSdkInfoWithTool(it, toolId)
                }
              }
          } ?: bestProposalFromTools
          // No tools or not pyproject.toml at all? Use EP as a fallback
        }
      }?.let { SdkForModuleConfigInstruction.CreateSdkInfoWrapper(this, it) }
    }
    is SuggestedSdk.SameAs -> {
      // A workspace root is pyproject-based, so it is a Python project.
      suggestedSdk.parentModule.asPyProject()?.let { SdkForModuleConfigInstruction.SameAs(this, it, suggestedSdk.accordingTo) }
    }
  }
