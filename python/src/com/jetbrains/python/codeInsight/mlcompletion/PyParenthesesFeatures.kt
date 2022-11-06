// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.mlcompletion

object PyParenthesesFeatures {
  fun haveOpeningRoundBracket(names: Map<String, Int>) = haveOpeningBracket(names, "(", ")")

  fun haveOpeningSquareBracket(names: Map<String, Int>) = haveOpeningBracket(names, "[", "]")

  fun haveOpeningBrace(names: Map<String, Int>): Boolean = haveOpeningBracket(names, "{", "}")

  private fun haveOpeningBracket(names: Map<String, Int>, openingBracket: String, closingBracket: String): Boolean {
    val cntOpening = names.entries.sumOf { if (it.key == openingBracket) it.value else 0 }
    val cntClosing = names.entries.sumOf { if (it.key == closingBracket) it.value else 0 }
    return cntOpening > cntClosing
  }
}