// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.types

import com.intellij.openapi.util.registry.RegistryValue
import com.intellij.openapi.util.registry.RegistryValueListener

/**
 * Runs the analysis again in each open project when the user changes [PyAnyType.REGISTRY_KEY].
 *
 * @see restartTypeAnalysis
 */
internal class PyAnyTypeRegistryListener : RegistryValueListener {
  override fun afterValueChanged(value: RegistryValue) {
    if (value.key != PyAnyType.REGISTRY_KEY) return
    restartTypeAnalysis("PyAnyTypeRegistryListener: ${PyAnyType.REGISTRY_KEY} changed")
  }
}
