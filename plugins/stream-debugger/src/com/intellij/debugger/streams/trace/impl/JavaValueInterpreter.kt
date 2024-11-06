// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.trace.impl

import com.intellij.debugger.engine.JavaValue
import com.intellij.debugger.streams.trace.XValueInterpreter
import com.intellij.psi.CommonClassNames
import com.intellij.xdebugger.frame.XValue
import com.sun.jdi.*


class JavaValueInterpreter : XValueInterpreter {
  override fun tryExtractArrayReference(result: XValue): ArrayReference? {
    if (result is JavaValue) {
      val reference: Value = result.descriptor.getValue()
      if (reference is ArrayReference) {
        return reference
      }
    }
    return null
  }

  override fun tryExtractErrorDescription(result: XValue): String? {
    if (result is JavaValue) {
      val reference: Value = result.descriptor.getValue()
      if (reference is ObjectReference) {
        var type = reference.referenceType() as? ClassType
        if (type != null) {
          while (type != null && CommonClassNames.JAVA_LANG_THROWABLE != type.name()) {
            type = type.superclass()
          }
          if (type != null) {
            val exceptionMessage: String? = tryExtractExceptionMessage(reference)
            val description = ("Evaluation failed: " + type.name()) + " exception thrown"
            val descriptionWithReason = if (exceptionMessage == null) description else "$description: $exceptionMessage"
            return descriptionWithReason
          }
        }
      }
    }
    return null
  }

  private fun tryExtractExceptionMessage(exception: ObjectReference): String? {
    val type = exception.referenceType()
    val messageField = DebuggerUtils.findField(type, "detailMessage");
    if (messageField == null) return null
    val message = exception.getValue(messageField)
    if (message is StringReference) {
      return message.value()
    }
    return null
  }
}