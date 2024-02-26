// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.console

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.module.Module
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface PyConsoleFrameworkParametersCustomizer {
  data class FrameworkConsoleParameters(
    val module: Module,
    val projectRoot: String,
    val consoleSettings: PyConsoleOptions.PyConsoleSettings,
    val consoleType: PyConsoleType,
  )

  fun getFrameworkConsoleParameters(module: Module): FrameworkConsoleParameters?

  companion object {
    val EP_NAME: ExtensionPointName<PyConsoleCustomizer> =
      ExtensionPointName.create("com.jetbrains.python.console.frameworkParametersCustomizer")
  }
}
