// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.debugger.coroutine.proxy.mirror

import com.intellij.debugger.impl.instanceOf
import com.sun.jdi.ObjectReference
import com.sun.jdi.ReferenceType
import org.jetbrains.kotlin.idea.debugger.base.util.evaluate.DefaultExecutionContext
import org.jetbrains.kotlin.idea.debugger.coroutine.util.logger

abstract class BaseMirror<T: ObjectReference, F>(val name: String, context: DefaultExecutionContext) : ReferenceTypeProvider, MirrorProvider<T, F> {
    val log by logger
    private val cls = context.findReferenceTypeSafe(name) ?: throw IllegalStateException("coroutine-debugger: class $name not found.")

    override fun getCls(): ReferenceType = cls

    override fun isCompatible(value: T?) =
        value?.referenceType()?.instanceOf(name) ?: false

    override fun mirror(value: T?, context: DefaultExecutionContext): F? {
        if (value == null) return null
        return if (!isCompatible(value)) {
            log.trace("Value ${value.referenceType()} is not compatible with $name.")
            null
        } else
            fetchMirror(value, context)
    }

    protected abstract fun fetchMirror(value: T, context: DefaultExecutionContext): F?
}
