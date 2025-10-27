package com.intellij.python.sdkConfigurator.common

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.project.projectId
import com.intellij.python.sdkConfigurator.common.impl.SdkConfiguratorBackEndApi


/**
 * New SDK configurator enabled
 */
val enableSDKAutoConfigurator: Boolean get() = Registry.`is`("intellij.python.sdkConfigurator.auto")


/**
 * it might ask user for list of modules and then detect SDK for them
 */
suspend fun detectSdkForModulesIn(project: Project) {
  SdkConfiguratorBackEndApi().configureAskingUser(project.projectId())
}
