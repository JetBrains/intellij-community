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

/**
 * @author Vitaliy.Bibaev
 */
interface ListVariable : Variable {
  val elementType: GenericType

  fun get(index: Expression): Expression
  fun get(index: Int): Expression = get(TextExpression(index.toString()))

  fun set(index: Expression, newValue: Expression): Expression
  fun set(index: Int, newValue: Expression): Expression = set(TextExpression(index.toString()), newValue)

  fun contains(element: Expression): Expression
  fun size(): Expression

  fun defaultDeclaration(isMutable: Boolean = true): VariableDeclaration
}