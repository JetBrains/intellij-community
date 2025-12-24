package com.intellij.python.sdkConfigurator.common

import com.intellij.openapi.util.registry.Registry


/**
 * New SDK configurator enabled
 */
val enableSDKAutoConfigurator: Boolean get() = Registry.`is`("intellij.python.sdkConfigurator.auto")

