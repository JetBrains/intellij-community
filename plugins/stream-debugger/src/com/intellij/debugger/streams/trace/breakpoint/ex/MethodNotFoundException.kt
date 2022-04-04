// Copyright 2000-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace.breakpoint.ex

import com.intellij.debugger.streams.trace.breakpoint.MethodSignature

/**
 * @author Shumaf Lovpache
 */
class MethodNotFoundException(val methodName: String, val signature: String, val type: String) : BreakpointTracingException(
  "Could not find method $type.$methodName($signature)") {
  constructor(signature: MethodSignature) : this(signature.name, signature.arguments, signature.containingClass)
}