// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.debugger

import com.intellij.openapi.extensions.ExtensionPointName
import com.jetbrains.python.run.EnvironmentController

interface PyDebugAsyncioCustomizer {
  companion object {
    private val EP_NAME: ExtensionPointName<PyDebugAsyncioCustomizer> =
      ExtensionPointName.create("com.jetbrains.python.debugger.pyDebugAsyncioCustomizer")

    val instance: PyDebugAsyncioCustomizer
      get() = EP_NAME.extensionList.first()
  }

  fun enableAsyncioMode(environmentController : EnvironmentController) {}

  fun isAsyncioModeSupported(): Boolean = false
}

class PyDebugAsyncioCustomizerDefault : PyDebugAsyncioCustomizer