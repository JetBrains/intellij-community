// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.wrapper.impl

import com.intellij.debugger.streams.trace.impl.handler.type.GenericType
import com.intellij.debugger.streams.wrapper.QualifierExpression
import com.intellij.openapi.util.TextRange

/**
 * @author Vitaliy.Bibaev
 */
class QualifierExpressionImpl(override val text: String,
                              override val textRange: TextRange,
                              private val typeAfter: GenericType) : QualifierExpression {
  override fun getTypeAfter(): GenericType = typeAfter
}
