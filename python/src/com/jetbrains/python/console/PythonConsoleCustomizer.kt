// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.console

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project

class PythonConsoleCustomizer : PyConsoleCustomizer {
  override fun guessConsoleModule(project: Project, contextModule: Module?): Module? {
    return contextModule
  }
}