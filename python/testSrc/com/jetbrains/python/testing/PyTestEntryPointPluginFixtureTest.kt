// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.testing

import com.intellij.idea.TestFor
import com.intellij.testFramework.UsefulTestCase.assertContainsElements
import com.intellij.testFramework.UsefulTestCase.assertDoesntContain
import com.jetbrains.python.allure.Components
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import com.jetbrains.python.psi.PyFunction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * The fixtures and the markers of the pytest plugins that the `pytest11` entry points of the installed distributions register.
 *
 * The class root [PLUGINS_ROOT] holds these packages:
 * - `pytest_django`, with a `.dist-info` directory. Its entry point is the module `pytest_django.plugin`, which imports the
 *   fixtures of `pytest_django/fixtures.py` as the real pytest-django does.
 * - `pytest_widgets`, with an `.egg-info` directory. Its entry point is `pytest_widgets:plugin`, so pytest registers the
 *   submodule `plugin` and not the package.
 * - `unregistered_fixtures`, with a fixture and a marker. No distribution registers it as a plugin.
 * - `pytest` and `_pytest`, with the built-in fixtures `tmp_path` and `monkeypatch`.
 *
 * A test that asserts a `TYPE` from a `.py` file of the class root turns off the check of the parsed class roots. The `TYPE`
 * directive also evaluates the user-initiated context, which reads the annotations of these files from their trees.
 * The highlighting test keeps the check on, so the code analysis of a plugin fixture stays on the stubs.
 */
@Subsystems.TestRunner
@Components.Pytest
@Layers.Functional
@TestFor(issues = ["PY-93651"])
@PyCodeInsightTestCase.UsePyTestRunner
class PyTestEntryPointPluginFixtureTest : PyCodeInsightTestCase() {

  @Test
  @TestCaseOptions(testFileName = TEST_FILE_NAME, additionalSdkRoots = [SdkRoot(PLUGINS_ROOT, OrderRootTypeEnum.CLASSES)])
  fun `the code analysis of a plugin fixture knows its type and reads no tree of a class root`() = test("""
    def test_home(client):
        client.get("/")
        client.post("/")  # WARNING Unresolved attribute reference 'post' for class 'Client'
    """)

  @Test
  @TestCaseOptions(testFileName = TEST_FILE_NAME, additionalSdkRoots = [SdkRoot(PLUGINS_ROOT, OrderRootTypeEnum.CLASSES)],
                   assertSdkRootsNotParsed = false)
  fun `a fixture that the plugin module imports has the return type of the fixture`() = test("""
    def test_home(client, rf):
        client
    #   └ TYPE Client
        rf
    #   └ TYPE RequestFactory
    """)

  @Test
  @TestCaseOptions(testFileName = TEST_FILE_NAME, additionalSdkRoots = [SdkRoot(PLUGINS_ROOT, OrderRootTypeEnum.CLASSES)],
                   assertSdkRootsNotParsed = false)
  fun `a fixture that the plugin module declares has the return type of the fixture`() = test("""
    def test_settings(django_db_modify_db_settings):
        django_db_modify_db_settings
    #   └ TYPE None
    """)

  @Test
  @TestCaseOptions(testFileName = TEST_FILE_NAME, additionalSdkRoots = [SdkRoot(PLUGINS_ROOT, OrderRootTypeEnum.CLASSES)],
                   assertSdkRootsNotParsed = false)
  fun `a module attribute entry point registers the module of the attribute and not the package`() = test("""
    def test_widget(widget, package_widget):
        widget
    #   └ TYPE Widget
        package_widget
    #   └ TYPE Unknown
    """)

  @Test
  @TestCaseOptions(testFileName = TEST_FILE_NAME, additionalSdkRoots = [SdkRoot(PLUGINS_ROOT, OrderRootTypeEnum.CLASSES)])
  fun `a fixture of a package that no entry point registers has no type`() = test("""
    def test_unregistered(unregistered):
        unregistered
    #   └ TYPE Unknown
    """)

  @Test
  @TestCaseOptions(testFileName = TEST_FILE_NAME, additionalSdkRoots = [SdkRoot(PLUGINS_ROOT, OrderRootTypeEnum.CLASSES)],
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
  @TestCaseOptions(testFileName = TEST_FILE_NAME, additionalSdkRoots = [SdkRoot(PLUGINS_ROOT, OrderRootTypeEnum.CLASSES)],
                   assertSdkRootsNotParsed = false)
  fun `a fixture of the pytest plugins of conftest overrides the fixture of the plugin`() = test("""
    def test_home(client):
        client
    #   └ TYPE str
    """, "conftest.py" to """
    pytest_plugins = ["project_fixtures"]
    """, "project_fixtures.py" to """
    import pytest

    @pytest.fixture
    def client() -> str:
        return ""
    """)

  @Test
  @TestCaseOptions(testFileName = TEST_FILE_NAME, additionalSdkRoots = [SdkRoot(PLUGINS_ROOT, OrderRootTypeEnum.CLASSES)],
                   assertSdkRootsNotParsed = false)
  fun `a built-in fixture of pytest keeps its type when plugins are installed`() = test("""
    def test_files(tmp_path):
        tmp_path
    #   └ TYPE Path
    """)

  @Test
  @TestCaseOptions(testFileName = TEST_FILE_NAME, additionalSdkRoots = [SdkRoot(PLUGINS_ROOT, OrderRootTypeEnum.CLASSES)],
                   assertSdkRootsNotParsed = false)
  fun `a fixture of the plugin overrides the built-in fixture of pytest with the same name`() = test("""
    def test_patch(monkeypatch):
        monkeypatch
    #   └ TYPE Widget
    """)

  @Test
  @TestCaseOptions(testFileName = TEST_FILE_NAME, additionalSdkRoots = [SdkRoot(PLUGINS_ROOT, OrderRootTypeEnum.CLASSES)],
                   assertSdkRootsNotParsed = false)
  fun `a marker of the plugins keeps the type of the markers of pytest`() = test("""
    import pytest

    marker = pytest.mark.django_db
    #└ TYPE MarkDecorator
    """)

  @Test
  @TestCaseOptions(testFileName = TEST_FILE_NAME, additionalSdkRoots = [SdkRoot(PLUGINS_ROOT, OrderRootTypeEnum.CLASSES)])
  fun `a fixture of the plugin resolves to its declaration`() {
    val declaration = withCaret("""
      def test_home(client):
      #             └ CARET
          pass
      """) { file -> file.findReferenceAt(myFixture.caretOffset)?.resolve() as? PyFunction }
    assertEquals("client", declaration?.name)
    assertEquals("$PLUGINS_ROOT/pytest_django/fixtures.py", declaration?.containingFile?.virtualFile?.path?.substringAfter("testData/"))
  }

  @Test
  @TestCaseOptions(testFileName = TEST_FILE_NAME, additionalSdkRoots = [SdkRoot(PLUGINS_ROOT, OrderRootTypeEnum.CLASSES)])
  fun `the members of the type of a fixture of the plugin complete`() {
    assertContainsElements(completeWithCaret("""
      def test_home(client):
          client.get("/")
      #          └ CARET
      """), "get")
  }

  @Test
  @TestCaseOptions(testFileName = TEST_FILE_NAME, additionalSdkRoots = [SdkRoot(PLUGINS_ROOT, OrderRootTypeEnum.CLASSES)])
  fun `the markers of the plugins complete after pytest mark`() {
    val variants = completeWithCaret("""
      import pytest

      @pytest.mark.django_db
      #            └ CARET
      def test_home():
          pass
      """)
    assertContainsElements(variants, "django_db", "urls")
    assertDoesntContain(variants, "unregistered_marker")
  }

  @Test
  @TestCaseOptions(testFileName = TEST_FILE_NAME, additionalSdkRoots = [SdkRoot(PLUGINS_ROOT, OrderRootTypeEnum.CLASSES)])
  fun `the keyword parameters of a marker of the plugins complete in its call`() {
    val variants = completeWithCaret("""
      import pytest

      @pytest.mark.django_db(transaction=True, )
      #                                        └ CARET
      def test_home():
          pass
      """)
    assertContainsElements(variants, "databases=", "reset_sequences=", "serialized_rollback=", "available_apps=")
    assertDoesntContain(variants, "transaction=")
  }

  private companion object {
    const val TEST_FILE_NAME = "test_plugins.py"
    const val PLUGINS_ROOT = "pytestEntryPointPlugins"
  }
}
