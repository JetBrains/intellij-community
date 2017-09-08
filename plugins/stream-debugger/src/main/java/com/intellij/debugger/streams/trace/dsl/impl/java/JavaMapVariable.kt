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

import com.intellij.debugger.streams.trace.dsl.Dsl
import com.intellij.debugger.streams.trace.dsl.Expression
import com.intellij.debugger.streams.trace.dsl.MapVariable
import com.intellij.debugger.streams.trace.dsl.VariableDeclaration
import com.intellij.debugger.streams.trace.dsl.impl.TextExpression
import com.intellij.debugger.streams.trace.dsl.impl.VariableImpl
import com.intellij.debugger.streams.trace.impl.handler.type.GenericType

/**
 * @author Vitaliy.Bibaev
 */
class JavaMapVariable(override val keyType: GenericType,
                      override val valueType: GenericType,
                      override val name: String,
                      private val linked: Boolean)
  : VariableImpl("${getMapType(linked)}<${keyType.genericTypeName}, ${valueType.genericTypeName}>",
                 name), MapVariable {

  companion object {
    fun getMapType(linked: Boolean): String = "java.util.${if (linked) "Linked" else ""}HashMap"
  }

  override fun get(key: Expression): Expression = TextExpression("$name.get(${key.toCode()})")

  override operator fun set(key: Expression, newValue: Expression): Expression =
    TextExpression("$name.put(${key.toCode()}, ${newValue.toCode()})")

  override fun contains(key: Expression): Expression = TextExpression("$name.contains(${key.toCode()})")

  override fun defaultDeclaration(isMutable: Boolean): VariableDeclaration =
    JavaVariableDeclaration(this, false, TextExpression("${getMapType(linked)}<>()"))

  override fun keys(): Expression = call("keySet")

  override fun size(): Expression = call("size")

  override fun convertToArray(dsl: Dsl, arrayName: String): String {
    val resultArray = dsl.array(GenericType.OBJECT.variableTypeName, arrayName)
    val size = dsl.variable("int", "size")
    val keys = dsl.array(keyType.variableTypeName, "keys")
    val values = dsl.array(valueType.variableTypeName, "values")
    val i = dsl.variable("int", "i")
    val key = dsl.variable(keyType.variableTypeName, "key")
    return dsl.code {
      declare(resultArray, true)
      scope {
        declare(size, size(), false)
        declare(keys.defaultDeclaration(size))
        declare(values.defaultDeclaration(size))
        declare(i, +"0", true)
        forEachLoop(key, keys()) {
          +keys.set(i, loopVariable)
          +values.set(i, get(loopVariable))
          +TextExpression("${i.toCode()}++")
        }

        resultArray.assign(newArray(resultArray.elementType, keys, values))
      }
    }
  }
}