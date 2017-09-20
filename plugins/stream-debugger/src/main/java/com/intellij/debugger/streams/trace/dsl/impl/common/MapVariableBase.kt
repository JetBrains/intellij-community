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
package com.intellij.debugger.streams.trace.dsl.impl.common

import com.intellij.debugger.streams.trace.dsl.CodeBlock
import com.intellij.debugger.streams.trace.dsl.Dsl
import com.intellij.debugger.streams.trace.dsl.MapVariable
import com.intellij.debugger.streams.trace.dsl.impl.TextExpression
import com.intellij.debugger.streams.trace.dsl.impl.VariableImpl
import com.intellij.debugger.streams.trace.impl.handler.type.MapType

/**
 * @author Vitaliy.Bibaev
 */
abstract class MapVariableBase(override val type: MapType, override val name: String)
  : VariableImpl(type, name), MapVariable {

  override fun convertToArray(dsl: Dsl, arrayName: String): CodeBlock {
    val resultArray = dsl.array(dsl.types.ANY, arrayName)
    val size = dsl.variable(dsl.types.INT, "size")
    val keys = dsl.array(type.keyType, "keys")
    val values = dsl.array(type.valueType, "values")
    val i = dsl.variable(dsl.types.INT, "i")
    val key = dsl.variable(type.keyType, "key")
    return dsl.block {
      declare(resultArray, true)
      scope {
        declare(size, size(), false)
        declare(keys.defaultDeclaration(size))
        declare(values.defaultDeclaration(size))
        declare(i, "0".expr, true)
        forEachLoop(key, keys()) {
          +keys.set(i, loopVariable)
          +values.set(i, get(loopVariable))
          +TextExpression("${i.toCode()}++")
        }

        resultArray.assign(newArray(dsl.types.ANY, keys, values))
      }
    }
  }
}