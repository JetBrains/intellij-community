// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.testing

import com.intellij.execution.actions.ConfigurationContext
import com.intellij.openapi.extensions.ExtensionPointName

/**
 * It is generally configuration provider duty to check if certain configuration is one from context and could be reused.
 * But this extension point allows external code to participate in this process
 */
interface PyTestConfigurationSelector {
  /**
   * See [PyTestsConfigurationProducer.isConfigurationFromContext]
   */
  fun isFromContext(configuration: PyAbstractTestConfiguration,
                    context: ConfigurationContext): Boolean

  companion object {
    val EP = ExtensionPointName.create<PyTestConfigurationSelector>("Pythonid.pyTestConfigurationSelector")
  }
}