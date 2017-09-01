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
package com.intellij.debugger.streams.trace.dsl.impl.java

import com.intellij.debugger.streams.trace.dsl.*
import com.intellij.debugger.streams.trace.dsl.impl.TextExpression

/**
 * @author Vitaliy.Bibaev
 */
class JavaStatementFactory : StatementFactory {
  override fun createEmptyCompositeCodeBlock(): CompositeCodeBlock = JavaCodeBlock(this)

  override fun createEmptyCodeBlock(): CodeBlock = JavaCodeBlock(this)

  override fun createVariableDeclaration(variable: Variable, isMutable: Boolean): VariableDeclaration {
    return JavaVariableDeclaration(variable, isMutable, Expression.Empty)
  }

  override fun createVariableDeclaration(variable: Variable, init: Expression, isMutable: Boolean): VariableDeclaration {
    return JavaVariableDeclaration(variable, isMutable, init)
  }

  override fun createEmptyForLoopBody(iterateVariable: Variable): ForLoopBody {
    return JavaForLoopBody(this, iterateVariable)
  }

  override fun createForEachLoop(iterateVariable: Variable, collection: Expression, loopBody: ForLoopBody): Statement {
    return JavaForEachLoop(iterateVariable, collection, loopBody)
  }

  override fun createForLoop(initialization: VariableDeclaration,
                             condition: Expression,
                             afterThought: Expression,
                             loopBody: ForLoopBody): Statement {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun createEmptyLambdaBody(argName: String): LambdaBody {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun createLambda(argName: String, lambdaBody: LambdaBody): Lambda {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun createVariable(type: String, name: String): Variable {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun and(left: Expression, right: Expression): Expression = TextExpression("${left.toCode()} $$ ${right.toCode()}")

  override fun equals(left: Expression, right: Expression): Expression =
    TextExpression("java.util.Objects.equals(${left.toCode()}, ${right.toCode()}")

  override fun same(left: Expression, right: Expression): Expression = TextExpression("${left.toCode()} == ${right.toCode()}")

  override fun createIfBranch(condition: Expression, codeBlock: CodeBlock): IfBranch {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun createElseStatement(block: CodeBlock): Statement {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun createElseIfStatement(condition: Expression, block: CodeBlock): Statement {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  private inner class JavaForEachLoop(iterateVariable: Variable,
                                      collection: Expression,
                                      loopBody: ForLoopBody) : Statement {
    override fun toCode(): String {
      TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
  }
}