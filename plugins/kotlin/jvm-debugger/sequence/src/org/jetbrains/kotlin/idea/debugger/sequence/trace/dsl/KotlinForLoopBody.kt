// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.debugger.sequence.trace.dsl

import com.intellij.debugger.streams.core.trace.dsl.Expression
import com.intellij.debugger.streams.core.trace.dsl.ForLoopBody
import com.intellij.debugger.streams.core.trace.dsl.StatementFactory
import com.intellij.debugger.streams.core.trace.dsl.Variable
import com.intellij.debugger.streams.core.trace.dsl.impl.TextExpression

class KotlinForLoopBody(
    override val loopVariable: Variable,
    statementFactory: StatementFactory
) : KotlinCodeBlock(statementFactory), ForLoopBody {
    private companion object {
        val BREAK = TextExpression("break")
    }

    override fun breakIteration(): Expression = BREAK
}