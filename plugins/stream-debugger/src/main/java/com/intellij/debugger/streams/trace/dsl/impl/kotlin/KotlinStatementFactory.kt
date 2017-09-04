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
package com.intellij.debugger.streams.trace.dsl.impl.kotlin

import com.intellij.debugger.streams.trace.dsl.*

/**
 * @author Vitaliy.Bibaev
 */
class KotlinStatementFactory : StatementFactory {
  override fun createEmptyCompositeCodeBlock(): CompositeCodeBlock {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun createEmptyCodeBlock(): CodeBlock {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun createVariableDeclaration(variable: Variable, isMutable: Boolean): VariableDeclaration {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun createVariableDeclaration(variable: Variable, init: Expression, isMutable: Boolean): VariableDeclaration {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun createEmptyForLoopBody(iterateVariable: Variable): ForLoopBody {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun createForEachLoop(iterateVariable: Variable, collection: Expression, loopBody: ForLoopBody): Statement {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
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

  override fun and(left: Expression, right: Expression): Expression {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun equals(left: Expression, right: Expression): Expression {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun same(left: Expression, right: Expression): Expression {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun createIfBranch(condition: Expression, thenBlock: CodeBlock): IfBranch {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }
}