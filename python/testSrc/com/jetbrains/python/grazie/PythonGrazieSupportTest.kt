// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.grazie

import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.GrazieTestBase
import com.intellij.grazie.jlanguage.Lang
import com.intellij.grazie.utils.TextStyleDomain

class PythonGrazieSupportTest : GrazieTestBase() {
  override fun getBasePath() = "python/testData/grazie/"
  override fun isCommunity() = true

  fun `test f-strings`() {
    runHighlightTestForFile("FStrings.py")
  }

  fun `test grammar check in constructs`() {
    runHighlightTestForFile("Constructs.py")
  }

  fun `test grammar check in docs`() {
    enableProofreadingFor(setOf(Lang.GERMANY_GERMAN, Lang.RUSSIAN))
    runHighlightTestForFile("Docs.py")
  }

  fun `test grammar check in comments`() {
    runHighlightTestForFile("Comments.py")
  }

  fun `test grammar check in string literals`() {
    runHighlightTestForFile("StringLiterals.py")
  }

  // PY-53047
  fun `test docstring tags are excluded`() {
    GrazieConfig.update { it.withDomainEnabledRules(TextStyleDomain.CodeDocumentation, enabledRules) }
    runHighlightTestForFile("DocstringTagsAreExcluded.py")
  }
}
