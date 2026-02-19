package com.intellij.ide.starter.runner

import com.intellij.ide.starter.config.ConfigurationStorage
import com.intellij.ide.starter.config.splitMode
import com.intellij.ide.starter.models.IdeInfo
import com.intellij.util.PlatformUtils

object AdditionalModulesForDevBuildServer {
  private val additionalModules: MutableSet<String> = mutableSetOf()
  private val additionalFrontendModules: MutableSet<String> = mutableSetOf()
  private val additionalBackendModules: MutableSet<String> = mutableSetOf()

  enum class IdeTarget {
    FRONTEND, BACKEND, ANY
  }

  private fun targetAdditionalModules(target: IdeTarget): MutableSet<String> {
    return when (target) {
      IdeTarget.FRONTEND -> additionalFrontendModules
      IdeTarget.BACKEND -> additionalBackendModules
      IdeTarget.ANY -> additionalModules
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

  internal fun getAdditionalModules(ideInfo: IdeInfo): List<String> {
    val result = additionalModules +
                 if (ideInfo.platformPrefix == PlatformUtils.JETBRAINS_CLIENT_PREFIX) {
                   additionalFrontendModules
                 }
                 else if (ConfigurationStorage.Companion.splitMode()) {
                   additionalBackendModules
                 }
                 else {
                   emptySet()
                 }
    return result.toList()
  }
}