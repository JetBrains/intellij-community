// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.trace.impl

import com.intellij.debugger.engine.DebuggerUtils
import com.intellij.debugger.engine.JavaValue
import com.intellij.debugger.streams.trace.XValueInterpreter
import com.intellij.psi.CommonClassNames
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.frame.XValue

class JavaValueInterpreter : XValueInterpreter {
  override fun tryExtractResult(session: XDebugSession, result: XValue): XValueInterpreter.Result? {
    if (result is JavaValue) {
      val reference = result.descriptor.getValue()
      if (reference is com.sun.jdi.ArrayReference) {
        return XValueInterpreter.Result(JvmArrayReference(reference), hasInnerExceptions(reference), JavaEvaluationContext(result.evaluationContext))
      }
    }
    return null
  }

  private fun hasInnerExceptions(resultArray: com.sun.jdi.ArrayReference): Boolean {
    val result = resultArray.getValue(1) as com.sun.jdi.ArrayReference
    val type: com.sun.jdi.ReferenceType = result.referenceType()
    if (type is com.sun.jdi.ArrayType) {
      if (type.componentTypeName().contains("Throwable")) {
        return true
      }
    }

    return false
  }

  override fun tryExtractError(result: XValue): String? {
    if (result is JavaValue) {
      val reference = result.descriptor.getValue()
      if (reference is com.sun.jdi.ObjectReference) {
        val type = reference.referenceType()
        var classType = type as? com.sun.jdi.ClassType
        if (classType != null) {
          while (classType != null && CommonClassNames.JAVA_LANG_THROWABLE != classType.name()) {
            classType = classType.superclass()
          }
          if (classType != null) {
            val exceptionMessage: String? = DebuggerUtils.tryExtractExceptionMessage(reference)
            val description = ("Evaluation failed: " + type.name()) + " exception thrown"
            val descriptionWithReason = if (exceptionMessage == null) description else "$description: $exceptionMessage"
            return descriptionWithReason
          }
        }
      }
    }
    return null
  }
}