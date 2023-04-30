// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.trace.breakpoint.old_formatters

import com.sun.jdi.ObjectReference
import com.sun.jdi.Value

data class StreamTraceValues(
  val intermediateOperationValues: List<ObjectReference>,
  val streamResult: Value,
  val time: ObjectReference,
  val elapsedTime: Value
)