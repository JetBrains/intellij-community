// Copyright 2000-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace.breakpoint.ex

import com.sun.jdi.Method
import com.sun.jdi.Value

class ArgumentTypeMismatchException(method: Method, actualArgs: List<Value?>) : TypeException(
  "Arguments mismatch when calling method ${method.name()}${method.signature()}, actual argument types: ${
    actualArgs.joinToString(", ") { it?.type()?.name().orEmpty() }
  }"
)
