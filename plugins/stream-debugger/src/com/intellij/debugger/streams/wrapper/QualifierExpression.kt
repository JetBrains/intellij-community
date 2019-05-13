// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.wrapper

import com.intellij.openapi.util.TextRange

/**
 * @author Vitaliy.Bibaev
 */
interface QualifierExpression : TypeAfterAware {
  val text: String
  val textRange: TextRange
}
