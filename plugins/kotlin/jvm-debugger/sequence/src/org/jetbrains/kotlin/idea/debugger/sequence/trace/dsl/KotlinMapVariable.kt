// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.debugger.sequence.trace.dsl

import com.intellij.debugger.streams.core.trace.dsl.*
import com.intellij.debugger.streams.core.trace.dsl.impl.TextExpression
import com.intellij.debugger.streams.core.trace.dsl.impl.common.MapVariableBase
import com.intellij.debugger.streams.core.trace.impl.handler.type.MapType

class KotlinMapVariable(type: MapType, name: String) : MapVariableBase(type, name) {
    override operator fun get(key: Expression): Expression = this.call("getValue", key)

    override operator fun set(key: Expression, newValue: Expression): Expression =
        TextExpression("${toCode()}[${key.toCode()}] = ${newValue.toCode()}")

    override fun contains(key: Expression): Expression = TextExpression("(${key.toCode()} in ${toCode()})")

    override fun size(): Expression = TextExpression("${toCode()}.size")

    override fun keys(): Expression = TextExpression("${toCode()}.keys")

    override fun computeIfAbsent(dsl: Dsl, key: Expression, valueIfAbsent: Expression, target: Variable): CodeBlock {
        return dsl.block {
            target assign call("computeIfAbsent", key, lambda("compIfAbsentKey") {
                doReturn(valueIfAbsent)
            })
        }
    }

    override fun defaultDeclaration(isMutable: Boolean): VariableDeclaration =
        KotlinVariableDeclaration(this, false, type.defaultValue)

    override fun entries(): Expression = TextExpression("${toCode()}.entries")
}