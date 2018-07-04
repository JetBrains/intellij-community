// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace.impl.handler.type

/**
 * @author Vitaliy.Bibaev
 */
class ListTypeImpl(override val elementType: GenericType, toName: (String) -> String, defaultValue: String)
  : ClassTypeImpl(toName.invoke(elementType.genericTypeName), defaultValue), ListType