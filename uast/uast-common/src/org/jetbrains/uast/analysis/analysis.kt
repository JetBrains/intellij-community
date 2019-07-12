// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.analysis

import org.jetbrains.uast.UExpression
import org.jetbrains.uast.getLanguagePlugin

fun <T : Any> UExpression.getExpressionFact(fact: UExpressionFact<T>): T? =
  this.getLanguagePlugin().analysisPlugin?.getExpressionFact(this, fact)

fun UExpression.canBeAnalyzedByUast() = this.getLanguagePlugin().analysisPlugin != null