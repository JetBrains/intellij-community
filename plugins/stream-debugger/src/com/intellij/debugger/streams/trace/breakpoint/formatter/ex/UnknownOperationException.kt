// Copyright 2000-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace.breakpoint.formatter.ex

import com.intellij.debugger.streams.trace.breakpoint.ex.BreakpointTracingException

class UnknownOperationException(operationName: String) : BreakpointTracingException(
  "Could not find formatter for operation $operationName")
