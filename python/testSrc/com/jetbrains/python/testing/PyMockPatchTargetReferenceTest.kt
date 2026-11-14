// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.testing

import com.intellij.idea.TestFor
import com.jetbrains.python.allure.Components
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import com.jetbrains.python.testing.pyMock.PyMockPatchTargetReferenceSet
import org.junit.jupiter.api.Test

/**
 * The references in the target string of `patch()` and the attribute string of `patch.object()`.
 */
@TestFor(classes = [PyMockPatchTargetReferenceSet::class])
@Subsystems.CodeInsight
@Components.Unittest
@Layers.Functional
class PyMockPatchTargetReferenceTest : PyCodeInsightTestCase() {

  @Test
  @TestCaseOptions(assertSdkRootsNotParsed = false)
  @TestFor(issues = ["PY-91732"])
  fun `f-string target with a placeholder is not reported as unresolved`() = test("""
    from unittest.mock import patch

    _MODULE = "os"

    @patch(f"{_MODULE}.getcwd")
    @patch(target=f"{_MODULE}.does_not_exist")
    @patch(f"{_MODULE}" ".does_not_exist")
    def test_foo(first_mock, second_mock, third_mock):
        pass
    """.trimIndent())

  @Test
  @TestCaseOptions(assertSdkRootsNotParsed = false)
  @TestFor(issues = ["PY-91732"])
  fun `f-string target with a placeholder in a context manager is not reported as unresolved`() = test("""
    from unittest.mock import patch

    _MODULE = "os"

    def test_foo():
        with patch(f"{_MODULE}.getcwd"):
            pass
        with patch("os.does_not_exist"):
        #              ^^^^^^^^^^^^^^ ERROR Unresolved reference 'does_not_exist'
            pass
    """.trimIndent())

  @Test
  @TestCaseOptions(assertSdkRootsNotParsed = false)
  @TestFor(issues = ["PY-91732"])
  fun `f-string attribute with a placeholder in patch object is not reported as unresolved`() = test("""
    import os
    from unittest.mock import patch

    _NAME = "getcwd"

    @patch.object(os, f"{_NAME}")
    @patch.object(os, f"does_not_exist")
    #                   ^^^^^^^^^^^^^^ ERROR Unresolved reference 'does_not_exist'
    def test_foo(first_mock, second_mock):
        pass
    """.trimIndent())

  @Test
  @TestCaseOptions(assertSdkRootsNotParsed = false)
  @TestFor(issues = ["PY-91732"])
  fun `f-string target with no placeholder is still checked`() = test("""
    from unittest.mock import patch

    @patch(f"os.does_not_exist")
    #           ^^^^^^^^^^^^^^ ERROR Unresolved reference 'does_not_exist'
    def test_foo(first_mock):
        pass
    """.trimIndent())

  @Test
  @TestCaseOptions(assertSdkRootsNotParsed = false)
  fun `plain string target that does not exist is reported as unresolved`() = test("""
    from unittest.mock import patch

    @patch("os.does_not_exist")
    #          ^^^^^^^^^^^^^^ ERROR Unresolved reference 'does_not_exist'
    def test_foo(first_mock):
        pass
    """.trimIndent())
}
