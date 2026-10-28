// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.readOnly

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.python.sdk.backend.PythonInterpreter
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

/** Tells whether packages cannot be installed into an interpreter, and why. */
@ApiStatus.Internal
interface PythonSdkReadOnlyProvider {
  fun isReadOnly(interpreter: PythonInterpreter): Boolean
  fun getReadOnlyMessage(interpreter: PythonInterpreter): @Nls String

  companion object {
    private val EP_NAME = ExtensionPointName.create<PythonSdkReadOnlyProvider>("Pythonid.pythonSdkReadOnlyProvider")

    internal fun isReadOnly(interpreter: PythonInterpreter): Boolean = EP_NAME.extensionList.any { it.isReadOnly(interpreter) }

    internal fun getReadOnlyMessage(interpreter: PythonInterpreter): @Nls String? =
      EP_NAME.extensionList.firstOrNull { it.isReadOnly(interpreter) }?.getReadOnlyMessage(interpreter)
  }
}
