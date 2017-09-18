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
package com.intellij.debugger.streams.trace.dsl

import com.intellij.debugger.streams.trace.dsl.impl.TextExpression
import com.intellij.debugger.streams.trace.impl.handler.type.GenericType
import com.intellij.debugger.streams.wrapper.IntermediateStreamCall

/**
 * @author Vitaliy.Bibaev
 */
interface DslFactory {
  fun lambda(argName: String, init: LambdaBody.(Expression) -> Unit): Lambda

  fun variable(type: GenericType, name: String): Variable

  fun array(elementType: GenericType, name: String): ArrayVariable

  fun list(elementType: GenericType, name: String): ListVariable

  fun newList(elementType: GenericType, vararg args: Expression): Expression

  fun newArray(elementType: GenericType, vararg args: Expression): Expression

  fun newSizedArray(elementType: GenericType, size: Expression): Expression

  fun newSizedArray(elementType: GenericType, size: Int): Expression = newSizedArray(elementType, +"$size")

  fun map(keyType: GenericType, valueType: GenericType, name: String): MapVariable

  fun linkedMap(keyType: GenericType, valueType: GenericType, name: String): MapVariable

  fun declaration(variable: Variable, init: Expression, isMutable: Boolean): VariableDeclaration

  operator fun String.unaryPlus(): TextExpression

  fun and(left: Expression, right: Expression): Expression

  fun equals(left: Expression, right: Expression): Expression

  fun same(left: Expression, right: Expression): Expression

  fun not(expression: Expression): Expression

  fun timeDeclaration(): VariableDeclaration

  fun currentTime(): Expression

  fun updateTime(): Expression

  fun createPeekCall(elementsType: GenericType, lambda: String): IntermediateStreamCall
}