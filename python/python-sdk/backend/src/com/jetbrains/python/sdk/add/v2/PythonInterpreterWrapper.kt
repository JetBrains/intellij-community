// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2

import com.intellij.python.sdk.backend.PythonInterpreter


data class PythonInterpreterWrapper<P>(val pythonInterpreter: PythonInterpreter, val homePath: P) {
  override fun toString(): String =
    "PythonInterpreterWrapper(pythonInterpreter=$pythonInterpreter, homePath=${(homePath as? PathHolder)?.toStringForUI() ?: homePath})"
}
