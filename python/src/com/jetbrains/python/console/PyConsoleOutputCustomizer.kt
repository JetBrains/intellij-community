// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.console

import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface PyConsoleOutputCustomizer {
  companion object {
    private val EP_NAME: ExtensionPointName<PyConsoleOutputCustomizer> =
      ExtensionPointName.create("com.jetbrains.python.console.pyConsoleOutputCustomizer")

    val instance: PyConsoleOutputCustomizer
      get() = EP_NAME.extensionList.first()
  }

  fun showRichOutput(consoleView: PythonConsoleView, data: Map<String, String>) {}

  fun isInlineOutputSupported(): Boolean = false
}

class PyConsoleOutputCustomizerDefault : PyConsoleOutputCustomizer