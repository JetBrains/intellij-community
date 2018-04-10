// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  fun newSizedArray(elementType: GenericType, size: Int): Expression = newSizedArray(elementType, "$size".expr)

  fun map(keyType: GenericType, valueType: GenericType, name: String): MapVariable

  fun linkedMap(keyType: GenericType, valueType: GenericType, name: String): MapVariable

  fun declaration(variable: Variable, init: Expression, isMutable: Boolean): VariableDeclaration

  val String.expr: Expression
    get() = TextExpression(this)

  infix fun Expression.and(right: Expression): Expression

  infix fun Expression.equals(right: Expression): Expression

  infix fun Expression.same(right: Expression): Expression

  operator fun Expression.not(): Expression

  fun timeDeclaration(): VariableDeclaration

  fun currentTime(): Expression

  fun updateTime(): Expression

  fun createPeekCall(elementsType: GenericType, lambda: String): IntermediateStreamCall
}