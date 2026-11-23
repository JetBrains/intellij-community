package com.intellij.python.pyproject.model.internal

import com.intellij.python.community.common.tools.ToolId
import com.intellij.python.pyproject.model.api.ModuleSdkState
import com.intellij.python.pyproject.model.api.SdkForModuleConfigInstruction
import com.intellij.python.pyproject.model.evolution.readStoredInterpreter
import com.intellij.python.pyproject.model.evolution.storedInterpreterKey
import com.intellij.python.sdk.backend.PythonInterpreterKey
import com.intellij.python.sdk.backend.PythonInterpreterRegistry
import com.jetbrains.python.project.project
import com.jetbrains.python.project.PyProject
import com.jetbrains.python.project.PyProject.Companion.asPyProject
import com.jetbrains.python.project.PyProject.Companion.getPyProjects
import com.jetbrains.python.sdk.configuration.CreateInterpreterInfo
import com.jetbrains.python.sdk.configuration.CreateSdkInfoWithTool
import com.jetbrains.python.sdk.configuration.PyProjectConfigurators
import com.jetbrains.python.sdk.configuration.PyProjectSdkConfigurationExtension
import com.jetbrains.python.sdk.configuration.findPythonVirtualEnvironments
import com.jetbrains.python.sdk.configuration.suggestToolInstallationFor

/**
 * See usage for an API doc
 */
internal suspend fun PyProject.getModuleSdkStateImpl(
  configuratorsByTool: Map<ToolId, PyProjectSdkConfigurationExtension> = PyProjectSdkConfigurationExtension.createMap(),
  fresh: Boolean = false,
): ModuleSdkState {
  // The stored entry decides, as the configuration lock needs a true answer at this instant. The entry names the
  // interpreter, so no configurator chooses another one for it.
  val key = residesOnModule.storedInterpreterKey()
  if (key != null) {
    val interpreter = PythonInterpreterRegistry.getInstance(project).findEnsuringRecord(key)
    if (interpreter != null) return ModuleSdkState.HasSdk(interpreter)
    return ModuleSdkState.Unavailable(key, unavailableInstruction(key, configuratorsByTool))
  }
  // The legacy flow names the interpreter by the SDK of the module.
  val interpreter = residesOnModule.readStoredInterpreter()
  if (interpreter != null) return ModuleSdkState.HasSdk(interpreter)
  return ModuleSdkState.NoSdk(suggestConfigInstruction(configuratorsByTool, fresh))
}

/**
 * The instruction for the unavailable interpreter of [key], or `null`. A machine without the tool of the manager of the
 * ref gets the suggestion to install it, see [suggestToolInstallationFor]. Otherwise the configurator of that manager
 * makes the environment again, see [PyProjectSdkConfigurationExtension.prepareSdkCreatorFor].
 */
private suspend fun PyProject.unavailableInstruction(
  key: PythonInterpreterKey,
  configuratorsByTool: Map<ToolId, PyProjectSdkConfigurationExtension>,
): SdkForModuleConfigInstruction? {
  // A member of a tool workspace can name the interpreter of the workspace root. The environment is made for the owner,
  // so the interpreter keeps the owner that the entry names.
  val owner = project.getPyProjects().firstOrNull { it.baseDir == key.ownerDir } ?: this
  val info = suggestToolInstallationFor(this, key.ref)?.let { CreateSdkInfoWithTool(it, ToolId(it.toolToInstall)) }
             ?: configuratorsByTool.firstNotNullOfOrNull { (toolId, configurator) ->
               configurator.prepareSdkCreatorFor(owner, key)?.let { CreateSdkInfoWithTool(it, toolId) }
             }
             ?: return null
  return SdkForModuleConfigInstruction.CreateSdkInfoWrapper(this, info)
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
