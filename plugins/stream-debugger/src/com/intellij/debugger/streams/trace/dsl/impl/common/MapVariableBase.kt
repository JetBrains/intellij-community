// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
      declare(resultArray, dsl.newSizedArray(dsl.types.ANY, 0), true)
      scope {
        declare(size, size(), false)
        declare(keys.defaultDeclaration(size))
        declare(values.defaultDeclaration(size))
        declare(i, "0".expr, true)
        forEachLoop(key, keys()) {
          statement { keys.set(i, loopVariable) }
          statement { values.set(i, get(loopVariable)) }
          statement { TextExpression("${i.toCode()}++") }
        }

        resultArray assign newArray(dsl.types.ANY, keys, values)
      }
    }
  }
}