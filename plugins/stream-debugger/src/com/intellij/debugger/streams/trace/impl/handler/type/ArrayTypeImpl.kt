// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace.impl.handler.type

/**
 * @author Vitaliy.Bibaev
 */
class ArrayTypeImpl(override val elementType: GenericType, toName: (String) -> String, private val toDefaultValue: (String) -> String)
  : ClassTypeImpl(toName.invoke(elementType.variableTypeName), toDefaultValue("1")), ArrayType {
  override fun sizedDeclaration(size: String): String = toDefaultValue(size)
}
