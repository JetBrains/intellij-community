// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.debugger.coroutine.proxy.mirror

import com.intellij.debugger.engine.DebuggerUtils
import com.sun.jdi.*
import org.jetbrains.kotlin.idea.debugger.base.util.evaluate.DefaultExecutionContext
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

interface ReferenceTypeProvider {
    fun getCls(): ReferenceType
}

interface MirrorProvider<T, F> {
    fun mirror(value: T?, context: DefaultExecutionContext): F?
    fun isCompatible(value: T?): Boolean
}

class MethodMirrorDelegate<T, F>(val name: String,
                                 private val mirrorProvider: MirrorProvider<T, F>,
                                 val signature: String? = null) : ReadOnlyProperty<ReferenceTypeProvider, MethodEvaluator.MirrorMethodEvaluator<T, F>> {
    override fun getValue(thisRef: ReferenceTypeProvider, property: KProperty<*>): MethodEvaluator.MirrorMethodEvaluator<T, F> {
        return MethodEvaluator.MirrorMethodEvaluator(DebuggerUtils.findMethod(thisRef.getCls(), name, signature), mirrorProvider)
    }
}

class MethodDelegate<T>(val name: String, val signature: String? = null) : ReadOnlyProperty<ReferenceTypeProvider, MethodEvaluator<T>> {
    override fun getValue(thisRef: ReferenceTypeProvider, property: KProperty<*>): MethodEvaluator<T> {
        return MethodEvaluator.DefaultMethodEvaluator(DebuggerUtils.findMethod(thisRef.getCls(), name, signature))
    }
}

class FieldMirrorDelegate<T, F>(val name: String,
                                private val mirrorProvider: MirrorProvider<T, F>) : ReadOnlyProperty<ReferenceTypeProvider, FieldEvaluator.MirrorFieldEvaluator<T, F>> {
    override fun getValue(thisRef: ReferenceTypeProvider, property: KProperty<*>): FieldEvaluator.MirrorFieldEvaluator<T, F> {
        return FieldEvaluator.MirrorFieldEvaluator(thisRef.getCls().fieldByName(name), thisRef, mirrorProvider)
    }
}

class FieldDelegate<T>(val name: String) : ReadOnlyProperty<ReferenceTypeProvider, FieldEvaluator<T>> {
    override fun getValue(thisRef: ReferenceTypeProvider, property: KProperty<*>): FieldEvaluator<T> {
        return FieldEvaluator.DefaultFieldEvaluator(thisRef.getCls().fieldByName(name), thisRef)
    }
}
