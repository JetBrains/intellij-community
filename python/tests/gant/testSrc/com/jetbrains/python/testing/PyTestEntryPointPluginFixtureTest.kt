// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.testing

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.idea.TestFor
import com.intellij.testFramework.UsefulTestCase.assertContainsElements
import com.intellij.testFramework.UsefulTestCase.assertDoesntContain
import com.intellij.testFramework.runInEdtAndWait
import com.jetbrains.python.allure.Components
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import com.jetbrains.python.psi.PyFunction
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * The fixtures of the pytest plugins that the `pytest11` entry points of the installed distributions register.
 *
 * The class root [PLUGINS_ROOT] holds the distributions `pytest_django`, with a `.dist-info` directory, and `pytest_widgets`,
 * with an `.egg-info` directory. The package `unregistered_fixtures` has a fixture, but no distribution registers it as a plugin.
 *
 * Each test turns off the check of the parsed class roots. The fixtures are in `.py` files, as in a real plugin. The `TYPE`
 * assertion, the completion and the navigation read the annotations of these files from their trees.
 */
@Subsystems.TestRunner
@Components.Pytest
@Layers.Functional
@TestFor(issues = ["PY-93651"])
class PyTestEntryPointPluginFixtureTest : PyCodeInsightTestCase() {
  private lateinit var originalFactory: PyAbstractTestFactory<*>

  @BeforeEach
  fun selectPyTestRunner() {
    val testRunnerService = TestRunnerService.getInstance(myFixture.module)
    originalFactory = testRunnerService.selectedFactory
    testRunnerService.selectedFactory = PythonTestConfigurationType.getInstance().pyTestFactory
  }

  @AfterEach
  fun restoreTestRunner() {
    TestRunnerService.getInstance(myFixture.module).selectedFactory = originalFactory
  }

  @Test
  @TestCaseOptions(testFileName = TEST_FILE_NAME, additionalSdkRoots = [SdkRoot(PLUGINS_ROOT)],
                   assertSdkRootsNotParsed = false)
  fun `a fixture that the plugin module imports has the return type of the fixture`() = test("""
    def test_home(client, rf):
        client
    #   └ TYPE Client
        rf
    #   └ TYPE RequestFactory
    """)

  @Test
  @TestCaseOptions(testFileName = TEST_FILE_NAME, additionalSdkRoots = [SdkRoot(PLUGINS_ROOT)],
                   assertSdkRootsNotParsed = false)
  fun `a fixture that the plugin module declares has the return type of the fixture`() = test("""
    def test_settings(django_db_modify_db_settings):
        django_db_modify_db_settings
    #   └ TYPE None
    """)

  @Test
  @TestCaseOptions(testFileName = TEST_FILE_NAME, additionalSdkRoots = [SdkRoot(PLUGINS_ROOT)],
                   assertSdkRootsNotParsed = false)
  fun `a named fixture of a package plugin of an egg-info distribution has the return type of the fixture`() = test("""
    def test_widget(widget):
        widget
    #   └ TYPE Widget
    """)

  @Test
  @TestCaseOptions(testFileName = TEST_FILE_NAME, additionalSdkRoots = [SdkRoot(PLUGINS_ROOT)],
                   assertSdkRootsNotParsed = false)
  fun `a fixture of a package that no entry point registers has no type`() = test("""
    def test_unregistered(unregistered):
        unregistered
    #   └ TYPE Unknown
    """)

  @Test
  @TestCaseOptions(testFileName = TEST_FILE_NAME, additionalSdkRoots = [SdkRoot(PLUGINS_ROOT)],
                   assertSdkRootsNotParsed = false)
  fun `a fixture of conftest overrides the fixture of the plugin`() = test("""
    def test_home(client):
        client
    #   └ TYPE str
    """, "conftest.py" to """
    import pytest

    @pytest.fixture
    def client() -> str:
        return ""
    """)

  @Test
  @TestCaseOptions(assertSdkRootsNotParsed = false)
  fun `a fixture of the plugin resolves to its declaration`() {
    withPluginsRoot {
      runInEdtAndWait {
        myFixture.configureByText(TEST_FILE_NAME, """
          def test_home(cli<caret>ent):
              pass
          """.trimIndent())
        val declaration = myFixture.file.findReferenceAt(myFixture.caretOffset)?.resolve() as? PyFunction
        assertEquals("client", declaration?.name)
        assertEquals("$PLUGINS_ROOT/pytest_django/fixtures.py", declaration?.containingFile?.virtualFile?.path?.substringAfter("testData/"))
      }
    }
  }

  @Test
  @TestCaseOptions(assertSdkRootsNotParsed = false)
  fun `the members of the type of a fixture of the plugin complete`() {
    withPluginsRoot {
      assertContainsElements(complete("""
        def test_home(client):
            client.<caret>
        """), "get")
    }
  }

  @Test
  @TestCaseOptions(assertSdkRootsNotParsed = false)
  fun `the markers of the plugins complete after pytest mark`() {
    withPluginsRoot {
      val variants = complete("""
        import pytest

        @pytest.mark.<caret>
        def test_home():
            pass
        """)
      assertContainsElements(variants, "django_db", "urls")
      assertDoesntContain(variants, "unregistered_marker")
    }
  }

  @Test
  @TestCaseOptions(assertSdkRootsNotParsed = false)
  fun `the keyword parameters of a marker of the plugins complete in its call`() {
    withPluginsRoot {
      val variants = complete("""
        import pytest

        @pytest.mark.django_db(transaction=True, <caret>)
        def test_home():
            pass
        """)
      assertContainsElements(variants, "databases=", "reset_sequences=", "serialized_rollback=", "available_apps=")
      assertDoesntContain(variants, "transaction=")
    }
  }

  @Test
  @TestCaseOptions(testFileName = TEST_FILE_NAME, additionalSdkRoots = [SdkRoot(PLUGINS_ROOT)],
                   assertSdkRootsNotParsed = false)
  fun `a marker of the plugins keeps the type of the markers of pytest`() = test("""
    import pytest

    marker = pytest.mark.django_db
    #└ TYPE MarkDecorator
    """)

  private fun withPluginsRoot(body: () -> Unit) {
    val roots = arrayOf(SdkRoot(PLUGINS_ROOT))
    setAdditionalSdkRoots(roots, true)
    try {
      body()
    }
    finally {
      setAdditionalSdkRoots(roots, false)
    }
  }

  /**
   * Returns the completion variants without an insertion.
   * An insertion edits the file and can start a suggested refactoring that fails in a later test.
   */
  private fun complete(@Language("Python") code: String): List<String> {
    val settings = CodeInsightSettings.getInstance()
    val autocomplete = settings.AUTOCOMPLETE_ON_CODE_COMPLETION
    var variants: List<String> = emptyList()
    try {
      settings.AUTOCOMPLETE_ON_CODE_COMPLETION = false
      runInEdtAndWait {
        myFixture.configureByText(TEST_FILE_NAME, code.trimIndent())
        myFixture.completeBasic()
        variants = myFixture.lookupElementStrings.orEmpty()
      }
    }
    finally {
      settings.AUTOCOMPLETE_ON_CODE_COMPLETION = autocomplete
    }
    return variants
  }

  private companion object {
    const val TEST_FILE_NAME = "test_plugins.py"
    const val PLUGINS_ROOT = "pytestEntryPointPlugins"
  }
}
