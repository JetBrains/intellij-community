package com.intellij.python.sdkConfigurator.common

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.rpc.topics.sendToClient
import com.intellij.python.sdkConfigurator.common.impl.DETECT_SDK_FOR_MODULES


/**
 * New SDK configurator enabled
 */
val enableSDKAutoConfigurator: Boolean get() = Registry.`is`("intellij.python.sdkConfigurator.auto")

/**
 * Check if [enableSDKAutoConfigurator] (return `false` if not) and start detecting process.
 * it might ask user for list of modules and then detect them
 */
fun detectSdkForModulesIn(project: Project): Boolean {
  if (!enableSDKAutoConfigurator) {
    return false
  }
  DETECT_SDK_FOR_MODULES.sendToClient(project, Unit)
  return true
}
