// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.grazie

import com.intellij.grazie.GrazieTestBase
import com.intellij.lang.Language
import com.jetbrains.python.PythonLanguage

class PythonGrazieSupportTest : GrazieTestBase() {
  override fun getBasePath() = "python/testData/grazie/"
  override fun isCommunity() = true

  override val additionalEnabledContextLanguages: Set<Language> = setOf(PythonLanguage.INSTANCE)

  fun `test grammar check in constructs`() {
    runHighlightTestForFile("Constructs.py")
  }

  fun `test grammar check in docs`() {
    runHighlightTestForFile("Docs.py")
  }

  fun `test grammar check in comments`() {
    runHighlightTestForFile("Comments.py")
  }

  fun `test grammar check in string literals`() {
    runHighlightTestForFile("StringLiterals.py")
  }
}
