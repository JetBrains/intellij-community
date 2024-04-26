// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.debugger.pydev.tables

import com.intellij.openapi.extensions.ExtensionPointName
import com.jetbrains.python.debugger.PyDebugValue
import com.jetbrains.python.debugger.PyFrameAccessor

interface PyNumericContainerPopupCustomizer {
  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<PyNumericContainerPopupCustomizer> =
      ExtensionPointName.create("com.jetbrains.python.debugger.numericContainerPopupCustomizer")
  }

  /** Returns true if the value could be processed. */
  fun showFullValuePopup(frameAccessor: PyFrameAccessor, debugValue: PyDebugValue): Boolean {
    frameAccessor.showNumericContainer(debugValue)
    return true
  }
}