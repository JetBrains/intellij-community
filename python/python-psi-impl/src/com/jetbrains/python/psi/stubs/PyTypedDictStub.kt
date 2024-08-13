// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.stubs

import com.jetbrains.python.psi.impl.stubs.CustomTargetExpressionStub
import java.util.Optional

interface PyTypedDictStub : CustomTargetExpressionStub {

  /**
   * @return TypedDict's name.
   */
  val name: String

  /**
   * @return keys' names and their values' types.
   * Iteration order repeats the declaration order.
   */
  val fields: List<PyTypedDictFieldStub>

  /**
   * @return value of 'total' keyword argument if exists, True otherwise
   */
  val isRequired: Boolean
}


data class PyTypedDictFieldStub(val name: String, val type: Optional<String>, val isReadOnly: Boolean)