// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.console

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project

interface PyConsoleCustomizer {
  companion object {
    val EP_NAME: ExtensionPointName<PyConsoleCustomizer> =
      ExtensionPointName.create("com.jetbrains.python.console.customizer")
  }

  fun guessConsoleModule(project: Project): Module?
}
