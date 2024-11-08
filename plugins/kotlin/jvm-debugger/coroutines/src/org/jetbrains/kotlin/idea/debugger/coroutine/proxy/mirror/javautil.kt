// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.debugger.coroutine.proxy.mirror

import com.sun.jdi.*
import org.jetbrains.kotlin.idea.debugger.base.util.evaluate.DefaultExecutionContext
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

class JavaLangObjectToString(context: DefaultExecutionContext) : BaseMirror<ObjectReference, String>("java.lang.Object", context) {
    private val toString by MethodDelegate<StringReference>("toString", "()Ljava/lang/String;")

    override fun fetchMirror(value: ObjectReference, context: DefaultExecutionContext): String {
        if (value is StringReference) {
            return value.value()
        }
        return toString.value(value, context)?.value() ?: ""
    }
}

class JavaUtilAbstractCollection(context: DefaultExecutionContext) :
        BaseMirror<ObjectReference, MirrorOfJavaLangAbstractCollection>("java.util.AbstractCollection", context) {
    private val toArrayMethod by MethodDelegate<ArrayReference>("toArray", "()[Ljava/lang/Object;")

    override fun fetchMirror(value: ObjectReference, context: DefaultExecutionContext): MirrorOfJavaLangAbstractCollection {
        val arrayValues = toArrayMethod.value(value, context)!!.values
        return MirrorOfJavaLangAbstractCollection(value, arrayValues.map { it as ObjectReference }.toList())
    }
}

class WeakReference(context: DefaultExecutionContext) :
        BaseMirror<ObjectReference, MirrorOfWeakReference>("java.lang.ref.WeakReference", context) {
    val get by MethodDelegate<ObjectReference>("get")

    override fun fetchMirror(value: ObjectReference, context: DefaultExecutionContext): MirrorOfWeakReference {
        return MirrorOfWeakReference(value, get.value(value, context))
    }
}

class StackTraceElement(context: DefaultExecutionContext) :
        BaseMirror<ObjectReference, MirrorOfStackTraceElement>("java.lang.StackTraceElement", context) {
    private val declaringClassField by FieldDelegate<StringReference>("declaringClass")
    private val methodNameField by FieldDelegate<StringReference>("methodName")
    private val fileNameField by FieldDelegate<StringReference>("fileName")
    private val lineNumberField by FieldDelegate<IntegerValue>("lineNumber")

    override fun fetchMirror(value: ObjectReference, context: DefaultExecutionContext): MirrorOfStackTraceElement? {
        // fetch all values at once
        val fieldValues = value.getValues(
            listOf(
                declaringClassField.field,
                methodNameField.field,
                fileNameField.field,
                lineNumberField.field
            )
        )
        val declaringClass = fieldValues[declaringClassField.field].safeAs<StringReference>()?.value() ?: return null
        val methodName = fieldValues[methodNameField.field].safeAs<StringReference>()?.value() ?: return null
        val fileName = fieldValues[fileNameField.field].safeAs<StringReference>()?.value()
        val lineNumber = fieldValues[lineNumberField.field].safeAs<IntegerValue>()?.value()
        return MirrorOfStackTraceElement(
            declaringClass,
            methodName,
            fileName,
            lineNumber,
        )
    }
}
