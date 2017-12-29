// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace.impl.handler.type

import java.util.Objects

/**
 * @author Vitaliy.Bibaev
 */
open class GenericTypeImpl(override val variableTypeName: String,
                           override val genericTypeName: String,
                           override val defaultValue: String) : GenericType {

  override fun hashCode(): Int {
    return Objects.hash(variableTypeName, genericTypeName)
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) {
      return true
    }

    return other is GenericType && variableTypeName == other.variableTypeName && genericTypeName == other.genericTypeName
  }

  override fun toString(): String = "variable: $variableTypeName, generic: $genericTypeName"
}
