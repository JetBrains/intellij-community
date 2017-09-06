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

import com.intellij.debugger.streams.trace.dsl.ArrayVariable
import com.intellij.debugger.streams.trace.dsl.Expression
import com.intellij.debugger.streams.trace.dsl.impl.TextExpression
import com.intellij.debugger.streams.trace.dsl.impl.VariableImpl

/**
 * @author Vitaliy.Bibaev
 */
class JavaArrayVariable(override val elementType: String, name: String) : VariableImpl("$elementType[]", name), ArrayVariable {
  override fun get(index: Expression): Expression = TextExpression("$name[${index.toCode()}]")

  override fun set(index: Expression, value: Expression): Expression = TextExpression("$name[${index.toCode()}] = ${value.toCode()}")
}