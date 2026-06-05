// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.trace.breakpoint.instrumentation

import com.intellij.debugger.streams.core.trace.impl.handler.type.GenericType
import com.intellij.debugger.streams.java.rt.StreamDebuggerUtils
import com.intellij.psi.CommonClassNames
import com.sun.jdi.ArrayReference
import com.sun.jdi.ObjectReference

internal fun ValueContext.addSequentialOperator(streamObject: ObjectReference): ObjectReference {
  return streamObject
    .referenceType()
    .method("sequential", "()Ljava/util/stream/BaseStream;")
    .invoke(streamObject) as ObjectReference
}

internal fun ValueContext.formatMap(map: ObjectReference, type: GenericType): ArrayReference {
  val streamTypeInfo = StreamTypeInfo.forType(type.genericTypeName)
  val utilsClass = clazz(StreamDebuggerUtils::class.java)
  val formatMethod = utilsClass.method(streamTypeInfo.formatterMethod, "(Ljava/util/Map;)[Ljava/lang/Object;")
  return formatMethod.invoke(utilsClass, listOf(map)) as ArrayReference
}

internal fun ValueContext.emptyResult(): ArrayReference {
  return array(
    array("int", 0),
    array(CommonClassNames.JAVA_LANG_OBJECT, 0)
  )
}
