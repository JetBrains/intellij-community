// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.debugger.coroutine.proxy.mirror

import com.sun.jdi.*
import org.jetbrains.kotlin.idea.debugger.base.util.evaluate.DefaultExecutionContext

class JavaLangObjectToString(context: DefaultExecutionContext) : BaseMirror<ObjectReference, String>("java.lang.Object", context) {
    private val toString by MethodDelegate<StringReference>("toString", "()Ljava/lang/String;")

    override fun fetchMirror(value: ObjectReference, context: DefaultExecutionContext): String {
        return toString.value(value, context)?.value() ?: ""
    }
}

class JavaUtilAbstractCollection(context: DefaultExecutionContext) :
        BaseMirror<ObjectReference, MirrorOfJavaLangAbstractCollection>("java.util.AbstractCollection", context) {
    private val abstractList = JavaUtilAbstractList(context)
    private val sizeMethod by MethodDelegate<IntegerValue>("size")

    override fun fetchMirror(value: ObjectReference, context: DefaultExecutionContext): MirrorOfJavaLangAbstractCollection {
        val list = mutableListOf<ObjectReference>()
        val size = sizeMethod.value(value, context)?.intValue() ?: 0
        for (index in 0 until size) {
            val reference = abstractList.get(value, index, context) ?: continue
            list.add(reference)
        }
        return MirrorOfJavaLangAbstractCollection(value, list)
    }
}

class JavaUtilAbstractList(context: DefaultExecutionContext) :
        BaseMirror<ObjectReference, ObjectReference>("java.util.AbstractList", context) {
    val getMethod by MethodDelegate<ObjectReference>("get")

    override fun fetchMirror(value: ObjectReference, context: DefaultExecutionContext): Nothing? =
            null

    fun get(value: ObjectReference, index: Int, context: DefaultExecutionContext): ObjectReference? =
            getMethod.value(value, context, context.vm.mirrorOf(index))
}

class WeakReference constructor(context: DefaultExecutionContext) :
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
        val declaringClass = declaringClassField.value(value)?.value() ?: return null
        val methodName = methodNameField.value(value)?.value() ?: return null
        val fileName = fileNameField.value(value)?.value()
        val lineNumber = lineNumberField.value(value)?.value()
        return MirrorOfStackTraceElement(
            declaringClass,
            methodName,
            fileName,
            lineNumber,
        )
    }
}
