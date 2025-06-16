// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.uv.run

import com.intellij.execution.configurations.ConfigurationTypeBase
import com.intellij.openapi.util.NotNullLazyValue
import com.jetbrains.python.PyBundle
import com.jetbrains.python.icons.PythonIcons

internal const val UV_CONFIGURATION_ID: String = "UvRunConfigurationType"

internal class UvRunConfigurationType :
  ConfigurationTypeBase(
    UV_CONFIGURATION_ID,
    PyBundle.message("uv.run.configuration.type.display.name"),
    PyBundle.message("uv.run.configuration.type.description"),
    NotNullLazyValue.createValue { PythonIcons.UV },
  ) {
  init {
    addFactory(UvRunConfigurationFactory(this))
  }
}
