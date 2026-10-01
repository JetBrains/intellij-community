// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.testing

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.idea.TestFor
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import com.jetbrains.python.inspections.unusedLocal.PyUnusedParameterInspection
import com.jetbrains.python.testing.pyMock.PyMockPatchArgumentCountInspection
import org.junit.jupiter.api.Test

/**
 * The parameters that get the mocks of `@patch` decorators, and the parameter count check.
 */
@TestFor(issues = ["PY-91259"], classes = [PyMockPatchArgumentCountInspection::class])
@Layers.Functional
class PyMockPatchArgumentCountTest : PyCodeInsightTestCase() {
  override val defaultInspections: Set<Class<out LocalInspectionTool>> = setOf(PyMockPatchArgumentCountInspection::class.java)

  @Test
  fun `pytest function gives fixtures to the parameters after the mocks`() = test("""
    from unittest.mock import patch

    @patch("os.getcwd")
    def test_foo(getcwd_mock, capsys):
    #            │            ^^^^^^ TYPE Unknown
    #            ^^^^^^^^^^^ TYPE MagicMock
        pass
    """.trimIndent())

  @Test
  fun `stacked mocks fill the first parameters from the innermost decorator`() = test("""
    from unittest.mock import patch, AsyncMock

    @patch("os.getcwd")
    @patch("os.listdir", new_callable=AsyncMock)
    @patch("os.remove", "replacement")
    def test_foo(listdir_mock, getcwd_mock, tmp_path, capsys):
    #            │             │            ^^^^^^^^ TYPE Unknown
    #            │             ^^^^^^^^^^^ TYPE MagicMock
    #            ^^^^^^^^^^^^ TYPE AsyncMock
        pass
    """.trimIndent())

  @Test
  fun `pytest method and fixture give fixtures to the parameters after the mocks`() = test("""
    import pytest
    from unittest.mock import patch

    @pytest.fixture
    @patch("os.getcwd")
    def cwd(getcwd_mock, tmp_path):
    #       ^^^^^^^^^^^ TYPE MagicMock
        return "/tmp"

    class TestFoo:
        @patch("os.getcwd")
        def test_foo(self, getcwd_mock, cwd, capsys):
    #                      │                 ^^^^^^ TYPE Unknown
    #                      ^^^^^^^^^^^ TYPE MagicMock
            pass

        @patch("os.getcwd")
        def test_star_args(self, getcwd_mock, capsys, *args):
    #                            │            ^^^^^^ TYPE Unknown
    #                            ^^^^^^^^^^^ TYPE MagicMock
            pass
    """.trimIndent())

  @Test
  fun `too few parameters for the mocks`() = test("""
    from unittest.mock import patch

    @patch("os.getcwd")
    @patch("os.listdir")
    def test_foo(listdir_mock): # WARNING Function needs 1 more parameter(s) for @patch injected mocks
        pass

    @patch("os.getcwd")
    def test_bar(*, getcwd_mock): # WARNING Function needs 1 more parameter(s) for @patch injected mocks
        pass

    @patch("os.getcwd")
    def check_cwd(): # WARNING Function needs 1 more parameter(s) for @patch injected mocks
        pass
    """.trimIndent())

  @Test
  fun `unittest test method has no fixtures`() = test("""
    import pytest
    import unittest
    from unittest.mock import patch

    class FooTest(unittest.TestCase):
        @patch("os.getcwd")
        def test_extra(self, getcwd_mock, capsys): # WARNING Function has 1 extra parameter(s) not matched by @patch decorators
    #                        ^^^^^^^^^^^ TYPE MagicMock
            pass

        @patch("os.getcwd")
        def test_keyword_only(self, getcwd_mock, *, flag): # WARNING Function has 1 extra parameter(s) not matched by @patch decorators
            pass

        @patch("os.getcwd")
        def test_default(self, getcwd_mock, flag=False, *, verbose=True):
    #                          ^^^^^^^^^^^ TYPE MagicMock
            pass

        @patch("os.getcwd")
        def setUp(self, getcwd_mock, extra): # WARNING Function has 1 extra parameter(s) not matched by @patch decorators
            pass

        @classmethod
        @patch("os.getcwd")
        def setUpClass(cls, getcwd_mock):
    #                       ^^^^^^^^^^^ TYPE MagicMock
            pass

        @staticmethod
        @patch("os.getcwd")
        def test_static(getcwd_mock, extra): # WARNING Function has 1 extra parameter(s) not matched by @patch decorators
            pass

        @patch("os.getcwd")
        def check_cwd(self, expected, getcwd_mock):
    #                       │         ^^^^^^^^^^^ TYPE MagicMock
    #                       ^^^^^^^^ TYPE Unknown
            pass

    @patch("os.listdir")
    class PatchedTest(unittest.TestCase):
        @patch("os.getcwd")
        def test_class_patch(self, getcwd_mock, listdir_mock):
    #                              │            ^^^^^^^^^^^^ TYPE MagicMock
    #                              ^^^^^^^^^^^ TYPE MagicMock
            pass

        @patch("os.getcwd")
        def test_class_patch_extra(self, getcwd_mock, listdir_mock, extra): # WARNING Function has 1 extra parameter(s) not matched by @patch decorators
            pass

        @patch("os.getcwd")
        def helper(self, getcwd_mock):
            pass

        @pytest.fixture(autouse=True)
        @patch("os.getcwd")
        def prepare(self, getcwd_mock, capsys):
    #                     ^^^^^^^^^^^ TYPE MagicMock
            pass
    """.trimIndent())

  @Test
  fun `other function gets the caller arguments before the mocks`() = test("""
    from unittest.mock import patch

    @patch("os.getcwd")
    def check_cwd(expected, getcwd_mock):
    #             │         ^^^^^^^^^^^ TYPE MagicMock
    #             ^^^^^^^^ TYPE Unknown
        pass

    check_cwd("/tmp")

    @patch("os.getcwd")
    def check_cwd_strict(expected, getcwd_mock, strict=False):
    #                    │         │            ^^^^^^ TYPE bool
    #                    │         ^^^^^^^^^^^ TYPE MagicMock
    #                    ^^^^^^^^ TYPE Unknown
        pass

    check_cwd_strict("/tmp")

    def test_outer():
        @patch("os.getcwd")
        def test_inner(expected, getcwd_mock):
    #                  │         ^^^^^^^^^^^ TYPE MagicMock
    #                  ^^^^^^^^ TYPE Unknown
            pass

        test_inner("/tmp")
    """.trimIndent())

  @Test
  fun `mock parameter is used and fixture parameter is not`() = test(TestOptions(enableInspections = setOf(PyUnusedParameterInspection::class.java)), """
    from unittest.mock import patch

    @patch("os.getcwd")
    def test_foo(getcwd_mock, capsys):
    #                         ^^^^^^ WEAK-WARNING Parameter 'capsys' value is not used
        pass
    """.trimIndent())
}
