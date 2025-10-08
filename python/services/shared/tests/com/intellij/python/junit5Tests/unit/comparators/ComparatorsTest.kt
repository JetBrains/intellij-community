// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit.comparators

import com.intellij.python.community.services.shared.LanguageLevelHolder
import com.intellij.python.community.services.shared.LanguageLevelWithUiComparator
import com.jetbrains.python.PyToolUIInfo
import com.intellij.python.community.services.shared.UiHolder
import com.jetbrains.python.psi.LanguageLevel
import org.hamcrest.MatcherAssert
import org.hamcrest.Matchers
import org.junit.jupiter.api.Test
import java.util.*

class ComparatorsTest {

  @Test
  fun testComparators() {
    val mocks = arrayOf(
      MockLevel(LanguageLevel.PYTHON314),
      MockLevel(LanguageLevel.PYTHON310),
      MockLevel(LanguageLevel.PYTHON310, ui = PyToolUIInfo("A")),
      MockLevel(LanguageLevel.PYTHON310, ui = PyToolUIInfo("Z")),
      MockLevel(LanguageLevel.PYTHON310, ui = PyToolUIInfo("B")),
      MockLevel(LanguageLevel.PYTHON27),
      MockLevel(LanguageLevel.PYTHON313),
    )
    val set = TreeSet(LanguageLevelWithUiComparator<MockLevel>())
    set.addAll(mocks)
    MatcherAssert.assertThat("", set, Matchers.contains(
      MockLevel(LanguageLevel.PYTHON314),
      MockLevel(LanguageLevel.PYTHON313),
      MockLevel(LanguageLevel.PYTHON310),
      MockLevel(LanguageLevel.PYTHON310, ui = PyToolUIInfo("A")),
      MockLevel(LanguageLevel.PYTHON310, ui = PyToolUIInfo("B")),
      MockLevel(LanguageLevel.PYTHON310, ui = PyToolUIInfo("Z")),
      MockLevel(LanguageLevel.PYTHON27)
    ))
  }
}

private data class MockLevel(
  override val languageLevel: LanguageLevel,
  override val ui: PyToolUIInfo? = null,
) : LanguageLevelHolder, UiHolder