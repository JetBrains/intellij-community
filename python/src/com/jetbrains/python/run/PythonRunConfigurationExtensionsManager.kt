// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.run

import com.intellij.execution.configuration.RunConfigurationExtensionsManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service

@Service
class PythonRunConfigurationExtensionsManager : RunConfigurationExtensionsManager<AbstractPythonRunConfiguration<*>, PythonRunConfigurationExtension>(PythonRunConfigurationExtension.EP_NAME) {
  companion object {
    @JvmStatic
    val instance: PythonRunConfigurationExtensionsManager
      get() = service()
  }
}
