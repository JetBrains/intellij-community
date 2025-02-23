// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.debugger.sequence.trace.dsl

import com.intellij.debugger.streams.core.trace.dsl.Expression
import com.intellij.debugger.streams.core.trace.dsl.LambdaBody
import com.intellij.debugger.streams.core.trace.dsl.StatementFactory

class KotlinLambdaBody(override val lambdaArg: Expression, statementFactory: StatementFactory) :
    KotlinCodeBlock(statementFactory), LambdaBody