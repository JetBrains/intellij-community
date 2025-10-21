// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit.comparators

import com.intellij.python.community.services.shared.PythonInfoHolder
import com.intellij.python.community.services.shared.PythonInfoWithUiComparator
import com.intellij.python.community.services.shared.UiHolder
import com.jetbrains.python.PyToolUIInfo
import com.jetbrains.python.PythonInfo
import com.jetbrains.python.psi.LanguageLevel
import org.hamcrest.MatcherAssert
import org.hamcrest.Matchers
import org.junit.jupiter.api.Test
import java.util.*

class ComparatorsTest {

  @Test
  fun testComparators() {
    val mocks = arrayOf(
      MockInfo(PythonInfo(LanguageLevel.PYTHON314)),
      MockInfo(PythonInfo(LanguageLevel.PYTHON314, true)),
      MockInfo(PythonInfo(LanguageLevel.PYTHON310)),
      MockInfo(PythonInfo(LanguageLevel.PYTHON310), ui = PyToolUIInfo("A")),
      MockInfo(PythonInfo(LanguageLevel.PYTHON310), ui = PyToolUIInfo("Z")),
      MockInfo(PythonInfo(LanguageLevel.PYTHON310), ui = PyToolUIInfo("B")),
      MockInfo(PythonInfo(LanguageLevel.PYTHON27)),
      MockInfo(PythonInfo(LanguageLevel.PYTHON313, true)),
      MockInfo(PythonInfo(LanguageLevel.PYTHON313)),
    )
    val set = TreeSet(PythonInfoWithUiComparator<MockInfo>())
    set.addAll(mocks)
    MatcherAssert.assertThat("", set, Matchers.contains(
      MockInfo(PythonInfo(LanguageLevel.PYTHON314)),
      MockInfo(PythonInfo(LanguageLevel.PYTHON314, true)),
      MockInfo(PythonInfo(LanguageLevel.PYTHON313)),
      MockInfo(PythonInfo(LanguageLevel.PYTHON313, true)),
      MockInfo(PythonInfo(LanguageLevel.PYTHON310)),
      MockInfo(PythonInfo(LanguageLevel.PYTHON310), ui = PyToolUIInfo("A")),
      MockInfo(PythonInfo(LanguageLevel.PYTHON310), ui = PyToolUIInfo("B")),
      MockInfo(PythonInfo(LanguageLevel.PYTHON310), ui = PyToolUIInfo("Z")),
      MockInfo(PythonInfo(LanguageLevel.PYTHON27))
    ))
  }
}

private data class MockInfo(
  override val pythonInfo: PythonInfo,
  override val ui: PyToolUIInfo? = null,
) : PythonInfoHolder, UiHolder
