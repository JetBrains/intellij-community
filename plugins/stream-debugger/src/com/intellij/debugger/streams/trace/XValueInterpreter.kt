// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.trace

import com.intellij.xdebugger.frame.XValue
import com.sun.jdi.ArrayReference
import org.jetbrains.annotations.Nls

interface XValueInterpreter {
  fun tryExtractArrayReference(result: XValue): ArrayReference?
  fun tryExtractErrorDescription(result: XValue): @Nls String?
}