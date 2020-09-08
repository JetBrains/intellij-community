// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.wrapper

import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.TextRange
import org.jetbrains.annotations.Nls

/**
 * @author Vitaliy.Bibaev
 */
interface QualifierExpression : TypeAfterAware {
  @get:NlsSafe val text: String
  val textRange: TextRange
}
