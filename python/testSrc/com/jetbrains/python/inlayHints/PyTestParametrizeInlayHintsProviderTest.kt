// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inlayHints

import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Components

import com.intellij.idea.TestFor
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.utils.inlays.declarative.DeclarativeInlayHintsProviderTestCase
import com.jetbrains.python.fixtures.PyLightProjectDescriptor
import com.jetbrains.python.psi.LanguageLevel

@Subsystems.CodeInsight
@Components.InlayHints
@Layers.Functional
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

  @TestFor(issues = ["PY-91742"])
  fun `test values after a spread match the last parameters`() {
    doTest("""
      import pytest

      def get_ids() -> list[int]: ...

      @pytest.mark.parametrize("id_for_payment, id_for_db, plugin_fields", [
          (*get_ids(), /*<# plugin_fields #>*/"foo"),
      ])
      def test_something(id_for_payment, id_for_db, plugin_fields): ...
    """)
  }

  @TestFor(issues = ["PY-91742"])
  fun `test values before a spread match the first parameters`() {
    doTest("""
      import pytest

      def get_rest() -> list[int]: ...

      @pytest.mark.parametrize("a, b, c", [
          (/*<# a #>*/1, *get_rest()),
      ])
      def test_something(a, b, c): ...
    """)
  }

  @TestFor(issues = ["PY-91742"])
  fun `test values between two spreads get no hint`() {
    doTest("""
      import pytest

      def get_some() -> list[int]: ...

      @pytest.mark.parametrize("a, b, c, d, e", [
          (/*<# a #>*/1, *get_some(), 3, *get_some(), /*<# e #>*/5),
      ])
      def test_something(a, b, c, d, e): ...
    """)
  }

  @TestFor(issues = ["PY-91742"])
  fun `test pytest param with a spread and marks`() {
    doTest("""
      import pytest

      def get_ids() -> list[int]: ...

      @pytest.mark.parametrize("id_for_payment, id_for_db, plugin_fields", [
          pytest.param(*get_ids(), /*<# plugin_fields #>*/"foo", marks=pytest.mark.skip),
      ])
      def test_something(id_for_payment, id_for_db, plugin_fields): ...
    """)
  }

  @TestFor(issues = ["PY-91742"])
  fun `test keyword arguments of pytest param get no hint`() {
    doTest("""
      import pytest

      def get_ys() -> list[int]: ...
      
      @pytest.mark.parametrize("x, y", [
          pytest.param(/*<# x #>*/1, *get_ys(), id="one"),
          pytest.param(/*<# x #>*/1, **{"marks": ()}),
      ])
      def test_function(x, y): ...
    """)
  }

  @TestFor(issues = ["PY-91742"])
  fun `test no hints after a spread when the trailing values outnumber the parameters`() {
    doTest("""
      import pytest

      def get_some() -> list[int]: ...

      @pytest.mark.parametrize("a, b", [
          (*get_some(), 1, 2, 3),
      ])
      def test_something(a, b): ...
    """, verifyHintsPresence = false)
  }

  @TestFor(issues = ["PY-91742"])
  fun `test elements of a spread display match their parameters`() {
    doTest("""
      import pytest

      @pytest.mark.parametrize("a, b, c", [
          (/*<# a #>*/1, *(/*<# b #>*/2, /*<# c #>*/3)),
          pytest.param(*[/*<# a #>*/1, /*<# b #>*/2], /*<# c #>*/3),
      ])
      def test_something(a, b, c): ...
    """)
  }

  @TestFor(issues = ["PY-91742"])
  fun `test values after a fixed-length tuple spread match their parameters`() {
    doTest("""
      import pytest

      PAIR = (1, 2)

      def get_some() -> list[int]: ...

      @pytest.mark.parametrize("a, b, c, d", [
          (*PAIR, /*<# c #>*/3, *get_some()),
          (*PAIR, /*<# c #>*/3, /*<# d #>*/4, 5),
          (/*<# a #>*/0, *PAIR, /*<# d #>*/4),
          pytest.param(*PAIR, *PAIR),
      ])
      def test_something(a, b, c, d): ...
    """)
  }

  @TestFor(issues = ["PY-91742"])
  fun `test a spread display with a spread of unknown length`() {
    doTest("""
      import pytest

      def get_some() -> list[int]: ...

      @pytest.mark.parametrize("a, b, c", [
          (*(/*<# a #>*/1, *get_some()), /*<# c #>*/3),
      ])
      def test_something(a, b, c): ...
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
