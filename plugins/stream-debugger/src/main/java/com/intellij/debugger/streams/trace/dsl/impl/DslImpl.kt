/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.debugger.streams.trace.dsl.impl

import com.intellij.debugger.streams.trace.dsl.*

/**
 * @author Vitaliy.Bibaev
 */
class DslImpl(override val statementFactory: StatementFactory) : Dsl, CodeBlock by statementFactory.createEmptyCompositeCodeBlock() {
  override val NULL: Expression = TextExpression("null")

  override val THIS: Expression = TextExpression("this")

  override fun variable(type: String, name: String): Variable = statementFactory.createVariable(type, name)

  override fun lambda(argName: String, init: LambdaBody.(Expression) -> Unit): Lambda {
    val lambdaBody = statementFactory.createEmptyLambdaBody(argName)
    lambdaBody.init(+argName)
    return statementFactory.createLambda(argName, lambdaBody)
  }

  override fun declaration(variable: Variable, init: Expression, isMutable: Boolean): VariableDeclaration =
    statementFactory.createVariableDeclaration(variable, init, isMutable)

  override fun String.unaryPlus(): TextExpression = TextExpression(this)
}