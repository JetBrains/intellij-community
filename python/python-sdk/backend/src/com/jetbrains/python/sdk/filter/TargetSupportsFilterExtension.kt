// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.filter

import com.intellij.execution.target.TargetEnvironmentConfiguration
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.platform.eel.EelDescriptor

/**
 * Tells if a target-based SDK with a configuration of type [C] can run on an eel of type [E].
 *
 * [EelDescriptor.filterAssignablePythonSdks] uses it only when the machine of the eel is not resolved yet.
 * If no extension supports a pair, the SDK is filtered out.
 */
interface TargetSupportsFilterExtension<C : TargetEnvironmentConfiguration, E : EelDescriptor> {
  val configurationClass: Class<C>
  val eelClass: Class<E>
  fun supports(config: C, eel: E): Boolean

  companion object {
    private val EP: ExtensionPointName<TargetSupportsFilterExtension<*, *>> = ExtensionPointName.create("Pythonid.targetSupportsFilter")

    fun supports(config: TargetEnvironmentConfiguration, eel: EelDescriptor): Boolean =
      EP.extensionList.any { it.supportsUntyped(config, eel) }

    private fun <C : TargetEnvironmentConfiguration, E : EelDescriptor> TargetSupportsFilterExtension<C, E>.supportsUntyped(
      config: TargetEnvironmentConfiguration,
      eel: EelDescriptor,
    ): Boolean {
      val typedConfig = configurationClass.takeIf { it.isInstance(config) }?.cast(config) ?: return false
      val typedEel = eelClass.takeIf { it.isInstance(eel) }?.cast(eel) ?: return false
      return supports(typedConfig, typedEel)
    }
  }
}
