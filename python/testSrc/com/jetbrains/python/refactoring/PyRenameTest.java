// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.refactoring;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.util.TextOccurrencesUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.PythonTestUtil;
import com.jetbrains.python.documentation.docstrings.DocStringFormat;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyTargetExpression;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


public class PyRenameTest extends PyTestCase {
  public static final String RENAME_DATA_PATH = "refactoring/rename/";

  public void testRenameField() {  // PY-457
    doTest("qu");
  }

  public void testSearchInStrings() {  // PY-670
    myFixture.configureByFile(RENAME_DATA_PATH + getTestName(true) + ".py");
    final PsiElement element = TargetElementUtil.findTargetElement(myFixture.getEditor(), TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED |
                                                                                          TargetElementUtil.ELEMENT_NAME_ACCEPTED);
    assertNotNull(element);
    myFixture.renameElement(element, "bar", true, false);
    myFixture.checkResultByFile(RENAME_DATA_PATH + getTestName(true) + "_after.py");
  }

  public void testRenameParameter() {  // PY-385
    doTest("qu");
  }

  public void testRenameParameterWithDecorator() { // PY-11858
    doTest("bar");
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
    runWithLanguageLevel(LanguageLevel.PYTHON27, () -> doTest("bar"));
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

  public void testRestRenameParameter() {
    doTest("bar");
  }

  public void testRestRenameType() {
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
    doTest("bar");
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

  // PY-9795
  public void testGoogleDocStringParam() {
    renameWithDocStringFormat(DocStringFormat.GOOGLE, "bar");
  }

  // PY-9795
  public void testGoogleDocStringAttribute() {
    renameWithDocStringFormat(DocStringFormat.GOOGLE, "bar");
  }

  // PY-9795
  public void testGoogleDocStringParamType() {
    renameWithDocStringFormat(DocStringFormat.GOOGLE, "Bar");
  }

  // PY-9795
  public void testGoogleDocStringReturnType() {
    renameWithDocStringFormat(DocStringFormat.GOOGLE, "Bar");
  }

  // PY-16761
  public void testGoogleDocStringPositionalVararg() {
    renameWithDocStringFormat(DocStringFormat.GOOGLE, "bar");
  }

  // PY-16761
  public void testGoogleDocStringKeywordVararg() {
    renameWithDocStringFormat(DocStringFormat.GOOGLE, "bar");
  }

  // PY-16908
  public void testNumpyDocStringCombinedParam() {
    renameWithDocStringFormat(DocStringFormat.NUMPY, "bar");
  }

  // PY-16760
  public void testGoogleDocstringAttributeRenamesWithClassAttribute() {
    renameWithDocStringFormat(DocStringFormat.GOOGLE, "bar");
  }

  // PY-28549
  public void testGoogleDocstringAttributeRenamesWithDataclassClassAttribute() {
    renameWithDocStringFormat(DocStringFormat.GOOGLE, "bar");
  }

  // PY-28549
  public void testGoogleDocstringDataClassParameterRenamesWithClassAttribute() {
    renameWithDocStringFormat(DocStringFormat.GOOGLE, "bar");
  }

  // PY-28549
  public void testGoogleDocstringDataClassParameterRenamesWithInitParameterOverClassAttribute() {
    renameWithDocStringFormat(DocStringFormat.GOOGLE, "bar");
  }

  // PY-2748
  public void testFormatStringDictLiteral() {
    doUnsupportedOperationTest();
  }

  // PY-2748
  public void testFormatStringNumericLiteralExpression() {
    doUnsupportedOperationTest();
  }
  
  // PY-19000
  public void testStringAsPositionalFormatFunctionArgument() {
    doUnsupportedOperationTest();
  }
  
  // PY-19000
  public void testSetAsPercentArg() {
    doUnsupportedOperationTest();
  }
  
  // PY-19000
  public void testListAsPercentArg() {
    doUnsupportedOperationTest();
  }
  
  // PY-19000
  public void testCallAsPercentArg() {
    doUnsupportedOperationTest();
  }
  
  // PY-19000
  public void testStarAsFormatFunctionArg() {
    doUnsupportedOperationTest();
  }
  
  // PY-19000
  public void testSubscriptionAsPercentArg() {
    doUnsupportedOperationTest();
  }
  
  // PY-19000
  public void testBinaryAsPercentArg() {
    doUnsupportedOperationTest();
  }
  
  // PY-19000
  public void testDictAsPercentArg() {
    doUnsupportedOperationTest();
  }

  // PY-22971
  public void testTopLevelOverloadsAndImplementationRenameOverload() {
    doTest("bar");
  }

  // PY-22971
  public void testTopLevelOverloadsAndImplementationRenameImplementation() {
    doTest("bar");
  }

  // PY-22971
  public void testTopLevelOverloadsAndImplementationRenameCall() {
    doTest("bar");
  }

  // PY-22971
  public void testOverloadsAndImplementationInClassRenameOverload() {
    doTest("bar");
  }

  // PY-22971
  public void testOverloadsAndImplementationInClassRenameImplementation() {
    doTest("bar");
  }

  // PY-22971
  public void testOverloadsAndImplementationInClassRenameCall() {
    doTest("bar");
  }

  // PY-22971
  public void testOverloadsAndImplementationInImportedModuleRenameCall() {
    doMultiFileTest("bar");
  }

  // PY-22971
  public void testOverloadsAndImplementationInImportedClassRenameCall() {
    doMultiFileTest("bar");
  }

  // PY-28199
  public void testCamelCaseToSnakeCaseTransformation() {
    assertEquals("foo", PyNameSuggestionProvider.toUnderscores("foo"));
    assertEquals("foo", PyNameSuggestionProvider.toUnderscores("Foo"));
    assertEquals("foo", PyNameSuggestionProvider.toUnderscores("FOO"));
    assertEquals("foo_bar", PyNameSuggestionProvider.toUnderscores("FooBar"));
    assertEquals("foo_bar", PyNameSuggestionProvider.toUnderscores("foo_bar"));
    assertEquals("foo_bar", PyNameSuggestionProvider.toUnderscores("FOO_BAR"));
    assertEquals("__foo_bar", PyNameSuggestionProvider.toUnderscores("__Foo_Bar"));
    assertEquals("foo42bar", PyNameSuggestionProvider.toUnderscores("foo42bar"));
    assertEquals("foo42_bar", PyNameSuggestionProvider.toUnderscores("foo42Bar"));
    assertEquals("foo42_bar", PyNameSuggestionProvider.toUnderscores("FOO42BAR"));
    assertEquals("foo_bar", PyNameSuggestionProvider.toUnderscores("FOOBar"));
    assertEquals("foo_bar_baz", PyNameSuggestionProvider.toUnderscores("foo_BarBAZ"));
  }

  // PY-27749
  public void testReferencesInsideFStringsNotReportedAsStringOccurrences() {
    myFixture.configureByFile(RENAME_DATA_PATH + getTestName(true) + ".py");
    final PyTargetExpression attr = (PyTargetExpression)myFixture.getElementAtCaret();
    final GlobalSearchScope singleFileScope = GlobalSearchScope.fileScope(myFixture.getFile());
    final List<PsiElement> found = new ArrayList<>();
    TextOccurrencesUtil.processUsagesInStringsAndComments(attr, singleFileScope, attr.getName(), true, (psiElement, textRange) -> {
      found.add(psiElement);
      return true;
    });
    assertEmpty(found);
  }

  // PY-21938
  public void testRenameMethodDefinitionDeclaredInPyi() {
    doMultiFileTest("someOtherMethodRenamed");
  }

  // PY-21938
  public void testRenameMethodUsageDeclaredInPyi() {
    doMultiFileTest("someOtherMethodRenamed");
  }

  // PY-21938
  public void testRenameMethodDeclaredInPyi() {
    doMultiFileTest("someOtherMethodRenamed", "a.pyi");
  }

  // PY-21937
  public void testRenameBothPyFileAndStub() {
    doMultiFileTest("bar.pyi");
  }

  // PY-29898
  public void testRenameDataclassAttributeAndKeywordArgument() {
    doTest("y");
  }

  // PY-48012
  public void testRenameKeywordParameter() {
    doTest("bar");
  }

  // PY-55231
  public void testRenameKeywordArgumentConstructorParameter() {
    doTest("taram");
  }

  // PY-63373
  public void testRenameTypeAliasFromItsDefinition() {
    doTest("Renamed");
  }

  // PY-63373
  public void testRenameTypeAliasFromItsUsage() {
    doTest("Renamed");
  }

  // PY-17733
  public void testRenameClassAttributeDefinedInClassMethod() {
    doTest("renamed");
  }

  // PY-79967
  public void testRenameVariableInTStringWithHTMLInjection() {
    doTest("username");
  }

  // PY-79967
  public void testRenameVariableInSimpleTemplateString() {
    doTest("username");
  }

  private void renameWithDocStringFormat(DocStringFormat format, final String newName) {
    runWithDocStringFormat(format, () -> doTest(newName));
  }

  private void doUnsupportedOperationTest() {
    myFixture.configureByFile(RENAME_DATA_PATH + getTestName(true) + ".py");
    try {
      myFixture.renameElementAtCaret("renamed");
    }
    catch (RuntimeException e) {
      if (e.getCause() instanceof IncorrectOperationException) {
        return;
      }
    }
    fail();
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
    doMultiFileTest(newName, "a.py");
  }

  private void doMultiFileTest(String newName, String entryFileName) {
    final String testName = getTestName(true);
    final VirtualFile dir1 = myFixture.copyDirectoryToProject(RENAME_DATA_PATH + testName + "/before", "");
    PsiDocumentManager.getInstance(myFixture.getProject()).commitAllDocuments();
    myFixture.configureFromTempProjectFile(entryFileName);
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
