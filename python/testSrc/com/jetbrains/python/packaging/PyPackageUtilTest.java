/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.packaging;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.module.Module;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class PyPackageUtilTest extends PyTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    myFixture.copyDirectoryToProject("packaging/PyPackageUtil/" + getTestName(false), "");
  }

  public void testAbsentSetupPyReading() {
    doTestSetupPyReading(false, false, false);
  }

  public void testAbsentSetupCallReading() {
    doTestSetupPyReading(true, false, false);
  }

  public void testAbsentSetupPyRequiresReading() {
    doTestSetupPyReading(true, true, false);
  }

  public void testSetupPyReading() {
    doTestSetupPyReading(true, true, true);
  }

  // PY-18966
  public void testSetupPyDependencyLinksReading() {
    final List<PyRequirement> actual = PyPackageUtil.findSetupPyRequires(myFixture.getModule());
    final List<PyRequirement> expected = PyRequirementParser.fromText(
      "sqlalchemy >=1.0.12, <1.1\ngit+https://github.com/mysql/mysql-connector-python.git@2.1.3#egg=mysql-connector-python-2.1.3");

    assertEquals(expected, actual);
  }

  public void testAbsentRequirementsTxtReading() {
    doTestRequirementsTxtReading(false, false);
  }

  public void testEmptyRequirementsTxtReading() {
    doTestRequirementsTxtReading(true, false);
  }

  public void testRequirementsTxtReading() {
    doTestRequirementsTxtReading(true, true);
  }

  public void testAbsentSetupPyUpdating() {
    doTestUselessRequirementsTxtOrSetupPyUpdating(false);
  }

  public void testAbsentSetupCallUpdating() {
    doTestUselessRequirementsTxtOrSetupPyUpdating(false);
  }

  public void testDistutilsSetupPyUpdating() {
    doTestSetupPyUpdating("requires");
  }

  public void testSetuptoolsSetupPyUpdating() {
    doTestSetupPyUpdating("install_requires");
  }

  public void testAbsentRequirementsTxtUpdating() {
    doTestUselessRequirementsTxtOrSetupPyUpdating(true);
  }

  public void testRequirementsTxtUpdating() {
    final Module module = myFixture.getModule();

    checkRequirements(PyPackageUtil.getRequirementsFromTxt(module), 1);

    WriteCommandAction.runWriteCommandAction(myFixture.getProject(),
                                             () -> PyPackageUtil.addRequirementToTxtOrSetupPy(module, "Markdown", LanguageLevel.PYTHON34));

    checkRequirements(PyPackageUtil.getRequirementsFromTxt(module));
  }

  private void doTestSetupPyReading(boolean hasFile, boolean hasCall, boolean requires) {
    final Module module = myFixture.getModule();

    if (hasFile) {
      assertTrue(PyPackageUtil.hasSetupPy(module));

      final PyFile setupPy = PyPackageUtil.findSetupPy(module);
      assertNotNull(setupPy);

      if (hasCall) {
        final PyCallExpression callByModule = PyPackageUtil.findSetupCall(module);
        final PyCallExpression callByFile = PyPackageUtil.findSetupCall(setupPy);

        assertNotNull(callByModule);
        assertNotNull(callByFile);
        assertSame(callByFile, callByModule);

        if (requires) {
          checkRequirements(PyPackageUtil.findSetupPyRequires(module));
        }
        else {
          final List<PyRequirement> requirements = PyPackageUtil.findSetupPyRequires(module);
          assertNotNull(requirements);
          assertEmpty(requirements);
        }
      }
      else {
        assertNull(PyPackageUtil.findSetupCall(module));
        assertNull(PyPackageUtil.findSetupCall(setupPy));
        assertNull(PyPackageUtil.findSetupPyRequires(module));
      }
    }
    else {
      assertFalse(PyPackageUtil.hasSetupPy(module));
      assertNull(PyPackageUtil.findSetupPy(module));
      assertNull(PyPackageUtil.findSetupCall(module));
      assertNull(PyPackageUtil.findSetupPyRequires(module));
    }
  }

  private void doTestRequirementsTxtReading(boolean hasFile, boolean hasRequirements) {
    final Module module = myFixture.getModule();

    if (hasFile) {
      assertTrue(PyPackageUtil.hasRequirementsTxt(module));
      assertNotNull(PyPackageUtil.findRequirementsTxt(module));

      if (hasRequirements) {
        checkRequirements(PyPackageUtil.getRequirementsFromTxt(module));
      }
      else {
        final List<PyRequirement> requirements = PyPackageUtil.getRequirementsFromTxt(module);
        assertNotNull(requirements);
        assertEmpty(requirements);
      }
    }
    else {
      assertFalse(PyPackageUtil.hasRequirementsTxt(module));
      assertNull(PyPackageUtil.findRequirementsTxt(module));
      assertNull(PyPackageUtil.getRequirementsFromTxt(module));
    }
  }

  private void doTestUselessRequirementsTxtOrSetupPyUpdating(boolean isRequirementsTxt) {
    final Module module = myFixture.getModule();

    assertNull(isRequirementsTxt ? PyPackageUtil.getRequirementsFromTxt(module) : PyPackageUtil.findSetupPyRequires(module));

    WriteCommandAction.runWriteCommandAction(myFixture.getProject(),
                                             () -> PyPackageUtil.addRequirementToTxtOrSetupPy(module, "Markdown", LanguageLevel.PYTHON34));

    assertNull(isRequirementsTxt ? PyPackageUtil.getRequirementsFromTxt(module) : PyPackageUtil.findSetupPyRequires(module));
  }

  private static void checkRequirements(@Nullable List<PyRequirement> actual) {
    checkRequirements(actual, 0);
  }

  private static void checkRequirements(@Nullable List<PyRequirement> actual, int fromIndex) {
    final List<PyRequirement> expected = PyRequirementParser.fromText("Markdown\nNewDjango==1.3.1\nnumpy\nmynose");
    assertEquals(expected.subList(fromIndex, expected.size()), actual);
  }

  private void doTestSetupPyUpdating(@NotNull String keyword) {
    final Module module = myFixture.getModule();

    checkSetupArgumentText(module, keyword, null);
    checkRequirements(PyPackageUtil.findSetupPyRequires(module), 2);

    final Runnable introduceRequires = () -> PyPackageUtil.addRequirementToTxtOrSetupPy(module, "NewDjango==1.3.1", LanguageLevel.PYTHON34);
    WriteCommandAction.runWriteCommandAction(myFixture.getProject(), introduceRequires);

    checkSetupArgumentText(module, keyword, "['NewDjango==1.3.1']");
    checkRequirements(PyPackageUtil.findSetupPyRequires(module), 1);

    final Runnable updateRequires = () -> PyPackageUtil.addRequirementToTxtOrSetupPy(module, "Markdown", LanguageLevel.PYTHON34);
    WriteCommandAction.runWriteCommandAction(myFixture.getProject(), updateRequires);

    checkSetupArgumentText(module, keyword, "['NewDjango==1.3.1', 'Markdown']");

    final List<PyRequirement> actual = PyPackageUtil.findSetupPyRequires(module);
    final List<PyRequirement> expected = PyRequirementParser.fromText("NewDjango==1.3.1\nMarkdown\nnumpy\nmynose");
    assertEquals(expected, actual);
  }

  private static void checkSetupArgumentText(@NotNull Module module, @NotNull String keyword, @Nullable String text) {
    final PyCallExpression setupCall = PyPackageUtil.findSetupCall(module);
    assertNotNull(setupCall);

    final PyExpression argument = setupCall.getKeywordArgument(keyword);

    if (text == null) {
      assertNull(argument);
    }
    else {
      assertNotNull(argument);
      assertEquals(text, argument.getText());
    }
  }
}