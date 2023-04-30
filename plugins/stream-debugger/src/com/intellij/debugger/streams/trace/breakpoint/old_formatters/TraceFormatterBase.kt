// Copyright 2000-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace.breakpoint.old_formatters

import com.intellij.debugger.engine.DebuggerUtils
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.streams.trace.breakpoint.DebuggerUtils.STREAM_DEBUGGER_UTILS_CLASS_NAME
import com.intellij.debugger.streams.trace.breakpoint.ValueManager
import com.intellij.debugger.streams.trace.breakpoint.ex.IncorrectValueTypeException
import com.intellij.psi.CommonClassNames.JAVA_LANG_OBJECT
import com.intellij.psi.CommonClassNames.JAVA_UTIL_MAP
import com.sun.jdi.ArrayReference
import com.sun.jdi.ClassType
import com.sun.jdi.ObjectReference
import com.sun.jdi.Value

/**
 * @author Shumaf Lovpache
 * Base class for intermediate operations (and for some terminal)
 */
open class TraceFormatterBase(private val valueManager: ValueManager,
                              private val evaluationContext: EvaluationContextImpl) : TraceFormatter {

  /**
   * Converts the result of the intermediate operation to the following format:
   * ```
   * var beforeArray = java.lang.Object[] { keys, values };
   * var afterArray = java.lang.Object[] { keys, values };
   * new java.lang.Object[] { beforeArray, afterArray };
   * ```
   */
  fun formatBeforeAfter(beforeValues: Value?, typeBefore: String,
                        afterValues: Value?, typeAfter: String): Value = valueManager.watch(evaluationContext) {
    val before = formatMap(beforeValues, typeBefore)
    val after = formatMap(afterValues, typeAfter)
    array(before, after)
  }

  protected fun formatMap(values: Value?, valuesType: String) = valueManager.watch(evaluationContext) {
    if (values == null) {
      emptyResult()
    }
    else {
      checkType(values)
      getMapKeysAndValues(values as ObjectReference, valuesType)
    }
  }

  private fun emptyResult(): ArrayReference = valueManager.watch(evaluationContext) {
    array(
      array("int", 0),
      array(JAVA_LANG_OBJECT, 0)
    )
  }

  private fun checkType(value: Value) {
    if (!(value is ObjectReference && DebuggerUtils.instanceOf(value.type(), JAVA_UTIL_MAP))) {
      throw IncorrectValueTypeException(JAVA_UTIL_MAP, value.type().name())
    }
  }

  private fun getMapKeysAndValues(valueMap: ObjectReference, valuesType: String): ArrayReference = valueManager.watch(evaluationContext) {
    val helperClass = getType(STREAM_DEBUGGER_UTILS_CLASS_NAME) as ClassType
    val formatMap = helperClass.method(getMapFormattingMethod(valuesType), "(Ljava/util/Map;)[Ljava/lang/Object;")
    formatMap.invoke(helperClass, listOf(valueMap)) as ArrayReference
  }

  private fun getMapFormattingMethod(valuesType: String) = when(valuesType) {
    "int" -> "formatIntMap"
    "long" -> "formatLongMap"
    "boolean" -> "formatBooleanMap"
    "double" -> "formatDoubleMap"
    else -> "formatObjectMap"
  }
}