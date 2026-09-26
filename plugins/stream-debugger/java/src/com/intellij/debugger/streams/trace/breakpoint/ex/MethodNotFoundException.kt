// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.trace.breakpoint.ex

import com.intellij.debugger.streams.trace.breakpoint.JvmMethodSignature

internal class MethodNotFoundException(val methodName: String, val signature: String, val type: String) : BreakpointTracingException(
"Could not find method $type.$methodName($signature)") {
  constructor(signature: JvmMethodSignature) : this(
    signature.name,
    signature.arguments,
    signature.classFqn
  )
}
