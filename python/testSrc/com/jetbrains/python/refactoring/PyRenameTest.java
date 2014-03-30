/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.jetbrains.python.refactoring;

import com.intellij.codeInsight.TargetElementUtilBase;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.testFramework.PlatformTestUtil;
import com.jetbrains.python.PythonTestUtil;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.LanguageLevel;

import java.io.IOException;

/**
 * @author yole
 */
public class PyRenameTest extends PyTestCase {
  public static final String RENAME_DATA_PATH = "refactoring/rename/";

  public void testRenameField() {  // PY-457
    doTest("qu");
  }

  public void testSearchInStrings() {  // PY-670
    myFixture.configureByFile(RENAME_DATA_PATH + getTestName(true) + ".py");
    final PsiElement element = TargetElementUtilBase.findTargetElement(myFixture.getEditor(), TargetElementUtilBase.REFERENCED_ELEMENT_ACCEPTED |
                                                                                        TargetElementUtilBase.ELEMENT_NAME_ACCEPTED);
    assertNotNull(element);
    myFixture.renameElement(element, "bar", true, false);
    myFixture.checkResultByFile(RENAME_DATA_PATH + getTestName(true) + "_after.py");
  }

  public void testRenameParameter() {  // PY-385
    doTest("qu");
  }

  public void testRenameMultipleDefinitionsLocal() {  // PY-727
    doTest("qu");
  }

  public void testRenameInheritors() {
    doTest("qu");
  }

  public void testRenameInitCall() {  // PY-1364
    doTest("Qu");
  }

  public void testRenameInstanceVar() {  // PY-1472
    doTest("_x");
  }

  public void testRenameLocalWithComprehension() {  // PY-1618
    doTest("bar");
  }

  public void testRenameLocalWithComprehension2() {  // PY-1618
    doTest("bar");
  }

  public void testRenameLocalWithGenerator() {  // PY-3030
    doTest("bar");
  }

  public void testRenameLocalWithNestedGenerators() {  // PY-3030
    doTest("bar");
  }

  public void testUpdateAll() {  // PY-986
    doTest("bar");
  }

  public void testEpydocRenameParameter() {
    doTest("bar");
  }

  public void testEpydocRenameType() {
    doTest("Shazam");
  }

  public void testRenameGlobal() {
    doTest("bar");
  }

  public void testRenameGlobalWithoutToplevel() { // PY-3547
    doTest("bar");
  }

  public void testRenameSlots() {  // PY-4195
    doTest("bacon");
  }
  
  public void testRenameKeywordArgument() {  // PY-3890
    doTest("baz");
  }
  
  public void testRenameTarget() {  // PY-5146
    doTest("bar");
  }

  public void testRenameAugAssigned() {  // PY-3698
    doTest("bar");
  }

  public void testRenameReassignedParameter() {  // PY-3698
    doTest("bar");
  }

  public void testRenameShadowingVariable() {  // PY-7342
    doTest("bar");
  }

  public void testRenameProperty() {  // PY-5948
    setLanguageLevel(LanguageLevel.PYTHON26);
    try {
      doTest("bar");
    }
    finally {
      setLanguageLevel(null);
    }
  }

  public void testClassNameConflict() {  // PY-2390
    doRenameConflictTest("Foo", "A class named 'Foo' is already defined in classNameConflict.py");
  }

  public void testClassVsFunctionConflict() {
    doRenameConflictTest("Foo", "A function named 'Foo' is already defined in classVsFunctionConflict.py");
  }

  public void testClassVsVariableConflict() {
    doRenameConflictTest("Foo", "A variable named 'Foo' is already defined in classVsVariableConflict.py");
  }

  public void testNestedClassNameConflict() {
    doRenameConflictTest("Foo", "A class named 'Foo' is already defined in class 'C'");
  }

  public void testFunctionNameConflict() {
    doRenameConflictTest("foo", "A function named 'foo' is already defined in functionNameConflict.py");
  }

  public void testVariableNameConflict() {
    doRenameConflictTest("foo", "A variable named 'foo' is already defined in variableNameConflict.py");
  }

  // PY-8315
  public void testRenamePropertyWithLambda() {
    doTest("bar");
  }

  // PY-8315
  public void testRenameOldStyleProperty() {
    doTest("bar");
  }

  // PY-8857
  public void testRenameImportSubModuleAs() {
    doMultiFileTest("bar.py");
  }

  // PY-8857
  public void testRenameImportModuleAs() {
    doMultiFileTest("bar.py");
  }

  // PY-9047
  public void testRenameSelfAndParameterAttribute() {
    doTest("bar");
  }

  // PY-4200
  public void testRenameUpdatesImportReferences() {
    doMultiFileTest("baz.py");
  }

  // PY-3991
  public void testRenamePackageUpdatesFirstFormImports() {
    doMultiFileTest("bar");
  }

  // PY-11879
  public void testDocstringParams() {
    doTest("bar");
  }

  private void doRenameConflictTest(String newName, String expectedConflict) {
    myFixture.configureByFile(RENAME_DATA_PATH + getTestName(true) + ".py");
    try {
      myFixture.renameElementAtCaret(newName);
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException ex) {
      assertEquals(expectedConflict, ex.getMessage());
      return;
    }
    fail("Expected conflict not reported");
  }

  private void doTest(final String newName) {
    myFixture.configureByFile(RENAME_DATA_PATH + getTestName(true) + ".py");
    myFixture.renameElementAtCaret(newName);
    myFixture.checkResultByFile(RENAME_DATA_PATH + getTestName(true) + "_after.py");
  }

  private void doMultiFileTest(String newName) {
    final String testName = getTestName(true);
    final VirtualFile dir1 = myFixture.copyDirectoryToProject(RENAME_DATA_PATH + testName + "/before", "");
    PsiDocumentManager.getInstance(myFixture.getProject()).commitAllDocuments();
    myFixture.configureFromTempProjectFile("a.py");
    myFixture.renameElementAtCaret(newName);
    VirtualFile dir2 = PyTestCase.getVirtualFileByName(PythonTestUtil.getTestDataPath() + "/" + RENAME_DATA_PATH + testName + "/after");
    try {
      PlatformTestUtil.assertDirectoriesEqual(dir2, dir1);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
