// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.expressions

import org.jetbrains.uast.UElement
import org.jetbrains.uast.UTypeReferenceExpression
import org.jetbrains.uast.internal.log

interface UTypeArgumentList : UElement {
  val arguments: List<UTypeReferenceExpression>

  override fun asLogString(): String = log()

  override fun asRenderString(): String = arguments.joinToString { it.asRenderString() }
}