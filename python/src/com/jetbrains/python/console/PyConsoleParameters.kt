// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.console

import org.jetbrains.annotations.ApiStatus
import com.intellij.openapi.module.Module


@ApiStatus.Internal
data class PyConsoleParameters(
  val module: Module,
  val projectRoot: String,
  val consoleSettings: PyConsoleOptions.PyConsoleSettings,
  val consoleType: PyConsoleType,
)
