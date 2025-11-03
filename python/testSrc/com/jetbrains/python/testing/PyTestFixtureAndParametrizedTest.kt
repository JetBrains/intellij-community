// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.testing

import com.intellij.idea.TestFor
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.jetbrains.python.fixtures.PyTestCase
import com.jetbrains.python.inspections.unusedLocal.PyUnusedLocalInspection
import com.jetbrains.python.testing.pyTestParametrized.PyTestParametrizedInspection

/**
 * Test py.test fixtures and parameterized completions and inspections
 */
class PyTestFixtureAndParametrizedTest : PyTestCase() {
  companion object {
    const val testSubfolder = "/testCompletion"
    fun testInspectionStatic(fixture: CodeInsightTestFixture) {
      fixture.configureByFile("test_for_inspection_test.py")
      fixture.enableInspections(PyUnusedLocalInspection::class.java, PyTestParametrizedInspection::class.java)
      fixture.checkHighlighting(true, false, true)
    }
  }

  override fun getTestDataPath() = super.getTestDataPath() + testSubfolder
  override fun setUp() {
    super.setUp()
    TestRunnerService.getInstance(myFixture.module).selectedFactory =
      PythonTestConfigurationType.getInstance().pyTestFactory
  }

  fun testInspection() {
    myFixture.copyDirectoryToProject(".", ".")
    testInspectionStatic(myFixture)
  }

  fun testTypeCompletion() {
    myFixture.copyDirectoryToProject(".", ".")
    myFixture.configureByFile("test_parametrized.py")
    myFixture.completeBasicAllCarets('\t')
    myFixture.checkResultByFile("after_test_parametrized.txt")
  }

  fun testCompletion() {
    myFixture.copyDirectoryToProject(".", ".")
    myFixture.configureByFile("test_test.py")
    myFixture.completeBasicAllCarets('\t')
    myFixture.checkResultByFile("after_test_test.txt")
  }

  @TestFor(issues = ["PY-54771"])
  fun `test usefixtures completion suggests fix on function`() {
    myFixture.copyDirectoryToProject(".", ".")
    myFixture.configureByFile("test_usefixtures_completion.py")
    myFixture.completeBasic()
    val variants = myFixture.lookupElementStrings
    assertNotNull(variants)
    assertTrue(variants!!.contains("my_fixture"))
    // Built-in pytest fixtures should not be suggested
    assertFalse(variants.contains("tmp_path"))
  }

  @TestFor(issues = ["PY-54771"])
  fun `test usefixtures completion suggests fix on class`() {
    myFixture.copyDirectoryToProject(".", ".")
    myFixture.configureByFile("test_usefixtures_completion_class.py")
    myFixture.completeBasic()
    val variants = myFixture.lookupElementStrings
    assertNotNull(variants)
    assertTrue(variants!!.contains("my_fixture"))
    // Built-in pytest fixtures should not be suggested
    assertFalse(variants.contains("tmp_path"))
  }

  @TestFor(issues = ["PY-54771"])
  fun `test usefixtures completion suggests fix on module`() {
    myFixture.copyDirectoryToProject(".", ".")
    myFixture.configureByFile("test_usefixtures_completion_module.py")
    myFixture.completeBasic()
    val variants = myFixture.lookupElementStrings
    assertNotNull(variants)
    assertTrue(variants!!.contains("my_fixture"))
    // Built-in pytest fixtures should not be suggested
    assertFalse(variants.contains("tmp_path"))
  }

  fun testClassLevelParametrizeCompletion() {
    myFixture.copyDirectoryToProject(".", ".")
    myFixture.configureByFile("test_class_level_parametrize.py")
    myFixture.completeBasic()
    val text = myFixture.editor.document.text
    assertTrue(text.contains("def test_(self, arg"))
  }

  fun testRename() {
    myFixture.configureByFile("test_for_rename.py")
    myFixture.renameElementAtCaret("spam")
    myFixture.checkResultByFile("test_for_rename.after.py.txt")
  }

  fun testRenameParameterWithType() {
    myFixture.configureByFile("test_rename_parameter_with_type.py")
    myFixture.renameElementAtCaret("new_fixture")
    myFixture.checkResultByFile("after_rename_with_type.txt")
  }

  fun testRenameFixtureWithType() {
    myFixture.configureByFile("test_rename_fixture_with_type.py")
    myFixture.renameElementAtCaret("new_fixture")
    myFixture.checkResultByFile("after_rename_with_type.txt")
  }

  fun testRenameParametrizeFirstParameter() {
    myFixture.configureByFile("test_rename_parametrize_first_param.py")
    myFixture.renameElementAtCaret("lst_1")
    myFixture.checkResultByFile("after_rename_parametrize_first_param.txt")
  }

  fun testRenameParametrizeSecondParameter() {
    myFixture.configureByFile("test_rename_parametrize_second_param.py")
    myFixture.renameElementAtCaret("lst_2")
    myFixture.checkResultByFile("after_rename_parametrize_second_param.txt")
  }

  fun testRenameParametrizeThirdParameter() {
    myFixture.configureByFile("test_rename_parametrize_third_param.py")
    myFixture.renameElementAtCaret("result")
    myFixture.checkResultByFile("after_rename_parametrize_third_param.txt")
  }

  fun testRenameParametrizeFunctionBody() {
    myFixture.configureByFile("test_rename_parametrize_function_body.py")
    myFixture.renameElementAtCaret("lst_2")
    myFixture.checkResultByFile("after_rename_parametrize_function_body.txt")
  }

  fun testRenameMultipleParametrizationFirstParam() {
    myFixture.configureByFile("test_rename_multiple_parametrization_first_param.py")
    myFixture.renameElementAtCaret("first")
    myFixture.checkResultByFile("after_rename_multiple_parametrization_first_param.txt")
  }

  fun testRenameMultipleParametrizationSecondParam() {
    myFixture.configureByFile("test_rename_multiple_parametrization_second_param.py")
    myFixture.renameElementAtCaret("second")
    myFixture.checkResultByFile("after_rename_multiple_parametrization_second_param.txt")
  }

  fun testRenamePreserveQualifiedAnnotation() {
    myFixture.configureByFile("test_rename_preserve_qualified_annotation.py")
    myFixture.renameElementAtCaret("abc")
    myFixture.checkResultByFile("after_rename_preserve_qualified_annotation.txt")
  }

  fun testParametrizeCoversTransitiveFixture() {
    myFixture.copyDirectoryToProject(".", ".")
    myFixture.configureByFile("test_parametrize_transitive_fixture.py")
    myFixture.enableInspections(PyTestParametrizedInspection::class.java)
    myFixture.checkHighlighting(true, false, true)
  }
}
