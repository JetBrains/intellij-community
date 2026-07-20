package com.intellij.ide.starter.runner

import com.intellij.ide.starter.config.ConfigurationStorage
import com.intellij.ide.starter.config.splitMode
import com.intellij.ide.starter.models.IdeInfo
import com.intellij.util.PlatformUtils

object AdditionalModulesForDevBuildServer {
  private val additionalModules: MutableSet<String> = mutableSetOf()
  private val additionalFrontendModules: MutableSet<String> = mutableSetOf()
  private val additionalBackendModules: MutableSet<String> = mutableSetOf()
  private val additionalMonolithModules: MutableSet<String> = mutableSetOf()

  class State internal constructor(
    internal val additionalModules: Set<String>,
    internal val additionalFrontendModules: Set<String>,
    internal val additionalBackendModules: Set<String>,
    internal val additionalMonolithModules: Set<String>,
  )

  enum class IdeTarget {
    FRONTEND, BACKEND, ANY, MONOLITH
  }

  private fun targetAdditionalModules(target: IdeTarget): MutableSet<String> {
    return when (target) {
      IdeTarget.FRONTEND -> additionalFrontendModules
      IdeTarget.BACKEND -> additionalBackendModules
      IdeTarget.ANY -> additionalModules
      IdeTarget.MONOLITH -> additionalMonolithModules
    }
  }

  fun addAdditionalModules(vararg additionalModulesToAdd: String, target: IdeTarget = IdeTarget.ANY) {
    check(!additionalModulesToAdd.contains(",")) { "Please add modules as separate arguments and not as a single string with comma separated modules" }
    targetAdditionalModules(target) += additionalModulesToAdd
  }

  fun removeAdditionalModules(vararg additionalModulesToRemove: String, target: IdeTarget = IdeTarget.ANY) {
    targetAdditionalModules(target) -= additionalModulesToRemove.toSet()
  }

  fun hasAdditionalModules(additionalModuleToCheck: String, vararg additionalModulesToCheck: String, target: IdeTarget = IdeTarget.ANY): Boolean {
    return targetAdditionalModules(target).containsAll(additionalModulesToCheck.toSet() + additionalModuleToCheck)
  }

  fun hasAnyAdditionalModules(target: IdeTarget = IdeTarget.ANY): Boolean {
    return targetAdditionalModules(target).isNotEmpty()
  }

  fun saveState(): State {
    return State(
      additionalModules = additionalModules.toSet(),
      additionalFrontendModules = additionalFrontendModules.toSet(),
      additionalBackendModules = additionalBackendModules.toSet(),
      additionalMonolithModules = additionalMonolithModules.toSet(),
    )
  }

  fun restoreState(state: State) {
    additionalModules.replaceWith(state.additionalModules)
    additionalFrontendModules.replaceWith(state.additionalFrontendModules)
    additionalBackendModules.replaceWith(state.additionalBackendModules)
    additionalMonolithModules.replaceWith(state.additionalMonolithModules)
  }

  internal fun getAdditionalModules(ideInfo: IdeInfo): List<String> {
    val result = additionalModules +
                 if (ideInfo.platformPrefix == PlatformUtils.JETBRAINS_CLIENT_PREFIX) {
                   additionalFrontendModules
                 }
                 else if (ConfigurationStorage.splitMode()) {
                   additionalBackendModules
                 }
                 else {
                   additionalMonolithModules
                 }
    return result.toList()
  }

  private fun MutableSet<String>.replaceWith(modules: Set<String>) {
    clear()
    addAll(modules)
  }
}
