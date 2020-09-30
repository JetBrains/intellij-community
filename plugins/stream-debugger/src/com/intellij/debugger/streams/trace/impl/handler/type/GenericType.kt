// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace.impl.handler.type

import com.intellij.openapi.util.NlsSafe


/**
 * @author Vitaliy.Bibaev
 */
interface GenericType {
  @get:NlsSafe
  val variableTypeName: String

  @get:NlsSafe
  val genericTypeName: String

  @get:NlsSafe
  val defaultValue: String

  interface CompositeType : GenericType {
    val elementType: GenericType
  }
}
