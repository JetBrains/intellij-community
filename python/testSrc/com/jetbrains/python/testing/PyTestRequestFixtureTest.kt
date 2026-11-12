// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.testing

import com.intellij.idea.TestFor
import com.intellij.testFramework.UsefulTestCase.assertContainsElements
import com.intellij.testFramework.UsefulTestCase.assertDoesntContain
import com.intellij.testFramework.runInEdtAndWait
import com.jetbrains.python.allure.Components
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import com.jetbrains.python.testing.pyTestFixtures.PyTextFixtureTypeProvider
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Test

@Subsystems.TestRunner
@Components.Pytest
@Layers.Functional
@TestFor(issues = ["PY-54421"], classes = [PyTextFixtureTypeProvider::class])
@PyCodeInsightTestCase.UsePyTestRunner
class PyTestRequestFixtureTest : PyCodeInsightTestCase() {

  @Test
  @TestCaseOptions(testFileName = TEST_FILE_NAME)
  fun `request in a fixture is a SubRequest`() = testWithPytest("""
    import pytest

    @pytest.fixture
    def my_fixture(request):
        return request.param
    #          └ TYPE SubRequest
    """)

  @Test
  @TestCaseOptions(testFileName = TEST_FILE_NAME)
  fun `request in a test function is a FixtureRequest`() = testWithPytest("""
    def test_foo(request):
        request
    #   └ TYPE FixtureRequest
    """)

  @Test
  @TestCaseOptions(testFileName = TEST_FILE_NAME)
  fun `request in a test method is a FixtureRequest`() = testWithPytest("""
    class TestFoo:
        def test_foo(self, request):
            request
    #       └ TYPE FixtureRequest
    """)

  @Test
  @TestCaseOptions(testFileName = TEST_FILE_NAME)
  fun `request in a function that is not a test has no type`() = testWithPytest("""
    def index(request):
        request
    #   └ TYPE Unknown
    """)

  @Test
  @TestCaseOptions(testFileName = TEST_FILE_NAME)
  fun `request without pytest has no type`() = test("""
    def test_foo(request):
        request
    #   └ TYPE Unknown
    """)

  @Test
  @TestCaseOptions(testFileName = TEST_FILE_NAME)
  fun `request is suggested for a fixture`() {
    assertContainsElements(completeWithCaret("""
      import pytest

      @pytest.fixture
      def my_fixture(reque):
      #                   └ CARET
          pass
      """), "request")
  }

  @Test
  @TestCaseOptions(testFileName = TEST_FILE_NAME)
  fun `request is suggested for a test function`() {
    assertContainsElements(completeWithCaret("""
      def test_foo(reque):
      #                 └ CARET
          pass
      """), "request")
  }

  @Test
  @TestCaseOptions(testFileName = TEST_FILE_NAME)
  fun `request is not suggested for a function that is not a test`() {
    assertDoesntContain(completeWithCaret("""
      def index(reque):
      #              └ CARET
          pass
      """), "request")
  }

  private fun testWithPytest(@Language("Python") code: String) {
    runInEdtAndWait {
      myFixture.addFileToProject("_pytest/fixtures.py", PYTEST_FIXTURES_STUB)
      myFixture.addFileToProject("pytest/__init__.py", "from _pytest.fixtures import fixture as fixture")
    }
    test(code)
  }

  private companion object {
    const val TEST_FILE_NAME = "test_request.py"

    val PYTEST_FIXTURES_STUB = """
      def fixture(function):
          return function

      class FixtureRequest:
          pass

      class SubRequest(FixtureRequest):
          param = None
    """.trimIndent()
  }
}
