// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inlayHints

import com.intellij.idea.TestFor
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.utils.inlays.declarative.DeclarativeInlayHintsProviderTestCase
import com.jetbrains.python.fixtures.PyLightProjectDescriptor
import com.jetbrains.python.psi.LanguageLevel

class PyTestParametrizeInlayHintsProviderTest : DeclarativeInlayHintsProviderTestCase() {

  fun `test simple parametrize with two parameters`() {
    doTest("""
      import pytest
      
      @pytest.mark.parametrize("input_num, expected", [
          (/*<# input_num #>*/2, /*<# expected #>*/4),
          (/*<# input_num #>*/3, /*<# expected #>*/9),
          (/*<# input_num #>*/4, /*<# expected #>*/16),
      ])
      def test_square(input_num, expected):
          assert input_num ** 2 == expected
    """)
  }

  fun `test parametrize with single parameter`() {
    doTest("""
      import pytest
      
      @pytest.mark.parametrize("value", [
          (/*<# value #>*/"hello",),
          (/*<# value #>*/"world",),
      ])
      def test_string_length(value):
          assert len(value) > 0
    """)
  }

  fun `test parametrize with three parameters`() {
    doTest("""
      import pytest
      
      @pytest.mark.parametrize("a, b, result", [
          (/*<# a #>*/1, /*<# b #>*/2, /*<# result #>*/3),
          (/*<# a #>*/5, /*<# b #>*/5, /*<# result #>*/10),
      ])
      def test_addition(a, b, result):
          assert a + b == result
    """)
  }

  fun `test no hints for non-tuple values`() {
    doTest("""
      import pytest
      
      @pytest.mark.parametrize("value", [1, 2, 3])
      def test_single_value(value):
          pass
    """, verifyHintsPresence = false)
  }

  fun `test no hints when parameter count mismatch`() {
    doTest("""
      import pytest
      
      @pytest.mark.parametrize("a, b", [
          (/*<# a #>*/1, /*<# b #>*/2, 3),  # Extra value, hints still shown for matched ones
      ])
      def test_mismatch(a, b):
          pass
    """)
  }

  fun `test class-level parametrize`() {
    doTest("""
      import pytest
      
      @pytest.mark.parametrize("x, y", [
          (/*<# x #>*/1, /*<# y #>*/2),
      ])
      class TestClass:
          def test_method(self, x, y):
              assert x < y
    """)
  }

  fun `test pytest param`() {
    doTest("""
      import pytest
      
      @pytest.mark.parametrize("x, y", [
          pytest.param(/*<# x #>*/1, /*<# y #>*/2),
          pytest.param(/*<# x #>*/3, /*<# y #>*/4),
      ])
      def test_function(x, y): ...
    """)
  }

  @TestFor(issues = ["PY-87820"])
  fun `test tuple`() {
    doTest("""
      import pytest
      
      @pytest.mark.parametrize("x, y", (
          (/*<# x #>*/1, /*<# y #>*/2),
      ))
      def test_function(x, y): ...
    """)
  }

  private fun doTest(text: String, verifyHintsPresence: Boolean = true) {
    doTestProvider(
      "test.py",
      text.trimIndent(),
      PyTestParametrizeInlayHintsProvider(),
      emptyMap(),
      verifyHintsPresence = verifyHintsPresence,
      testMode = ProviderTestMode.SIMPLE
    )
  }

  override fun getProjectDescriptor(): LightProjectDescriptor {
    return PyLightProjectDescriptor(LanguageLevel.getLatest())
  }
}
