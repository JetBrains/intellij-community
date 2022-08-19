// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.debugger.coroutine.proxy.mirror

import com.sun.jdi.LongValue
import com.sun.jdi.ObjectReference
import com.sun.jdi.StringReference
import org.jetbrains.kotlin.idea.debugger.base.util.evaluate.DefaultExecutionContext

class CoroutineContext(context: DefaultExecutionContext) :
    BaseMirror<ObjectReference, MirrorOfCoroutineContext>("kotlin.coroutines.CombinedContext", context) {
    private val coroutineNameRef = CoroutineName(context)
    private val coroutineIdRef = CoroutineId(context)
    private val dispatcherRef = CoroutineDispatcher(context)
    private val getContextElement by MethodDelegate<ObjectReference>("get")

    override fun fetchMirror(value: ObjectReference, context: DefaultExecutionContext): MirrorOfCoroutineContext {
        val coroutineName = getElementValue(value, context, coroutineNameRef)
        val coroutineId = getElementValue(value, context, coroutineIdRef)
        val dispatcher = getElementValue(value, context, dispatcherRef)
        return MirrorOfCoroutineContext(coroutineName, coroutineId, dispatcher)
    }

    private fun <T> getElementValue(value: ObjectReference, context: DefaultExecutionContext, keyProvider: ContextKey<T>): T? {
        val key = keyProvider.key() ?: return null
        val elementValue = getContextElement.value(value, context, key)
        return keyProvider.mirror(elementValue, context)
    }
}

abstract class ContextKey<T>(name: String, context: DefaultExecutionContext) : BaseMirror<ObjectReference, T>(name, context) {
    abstract fun key(): ObjectReference?
}

class CoroutineName(context: DefaultExecutionContext) : ContextKey<String>("kotlinx.coroutines.CoroutineName", context) {
    private val key by FieldDelegate<ObjectReference>("Key")
    private val getNameRef by MethodDelegate<StringReference>("getName")

    override fun fetchMirror(value: ObjectReference, context: DefaultExecutionContext): String? {
        return getNameRef.value(value, context)?.value()
    }

    override fun key() = key.staticValue()
}

class CoroutineId(context: DefaultExecutionContext) : ContextKey<Long>("kotlinx.coroutines.CoroutineId", context) {
    private val key by FieldDelegate<ObjectReference>("Key")
    private val getIdRef by MethodDelegate<LongValue>("getId")

    override fun fetchMirror(value: ObjectReference, context: DefaultExecutionContext): Long? {
        return getIdRef.value(value, context)?.longValue()
    }

    override fun key() = key.staticValue()
}

class CoroutineDispatcher(context: DefaultExecutionContext) : ContextKey<String>("kotlinx.coroutines.CoroutineDispatcher", context) {
    private val key by FieldDelegate<ObjectReference>("Key")
    private val jlm = JavaLangObjectToString(context)

    override fun fetchMirror(value: ObjectReference, context: DefaultExecutionContext): String? {
        return jlm.mirror(value, context)
    }

    override fun key() = key.staticValue()
}
