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
import org.junit.jupiter.api.Assertions
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

  /**
   * Check [Comparator.compare] contract for [PythonInfoWithUiComparator]
   */
  @Test
  fun testCompareContract() {
    val longName = " ".repeat(100)
    val data = mutableListOf<MockInfo>()
    for (lang in LanguageLevel.entries) {
      for (free in arrayOf(true, false)) {
        for (title in arrayOf(null, "uv", "homebrew", " ", "ÿ", longName)) {
          val ui = if (title == null) null else PyToolUIInfo(title)
          data.add(MockInfo(PythonInfo(lang, free), ui))
        }
      }
    }

    for (x in data) {
      Assertions.assertEquals(0, x.compareTo(x), "Reflexivity broken for $x")
      for (y in data) {
        Assertions.assertEquals(-(y.compareTo(x)), x.compareTo(y), "Antisymmetry broken for $x, $y")
        for (z in data) {
          if (x > y && y > z) {
            Assertions.assertTrue(x > z, "Transitivity broken for $x, $y, $z")
          }
        }
      }
    }
  }
}

private data class MockInfo(
  override val pythonInfo: PythonInfo,
  override val ui: PyToolUIInfo? = null,
) : PythonInfoHolder, UiHolder, Comparable<MockInfo> {

  override fun compareTo(other: MockInfo): Int = COMPARATOR.compare(this, other)
}

private val COMPARATOR = PythonInfoWithUiComparator<MockInfo>()
