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
package com.jetbrains.python;

import com.intellij.codeInsight.completion.impl.CamelHumpMatcher;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupElement;
import com.jetbrains.python.documentation.DocStringFormat;
import com.jetbrains.python.documentation.PyDocumentationSettings;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.impl.PythonLanguageLevelPusher;

import java.util.Arrays;
import java.util.List;

public class PythonCompletionTest extends PyTestCase {

  private void doTest() {
    CamelHumpMatcher.forceStartMatching(getTestRootDisposable());
    final String testName = "completion/" + getTestName(true);
    myFixture.configureByFile(testName + ".py");
    myFixture.completeBasic();
    myFixture.checkResultByFile(testName + ".after.py");
  }

  private void doMultiFileTest() {
    myFixture.copyDirectoryToProject("completion/" + getTestName(true), "");
    myFixture.configureByFile("a.py");
    myFixture.completeBasic();
    myFixture.checkResultByFile("completion/" + getTestName(true) + "/a.after.py");
  }

  public void testLocalVar() {
    doTest();
  }

  public void testSelfMethod() {
    doTest();
  }

  public void testSelfField() {
    doTest();
  }

  public void testFuncParams() {
    doTest();
  }

  public void testFuncParamsStar() {
    doTest();
  }

  public void testInitParams() {
    doTest();
  }

  public void testSuperInitParams() {      // PY-505
    doTest();
  }

  public void testSuperInitKwParams() {      // PY-778
    doTest();
  }

  public void testPredefinedMethodName() {
    doTest();
  }

  public void testPredefinedMethodNot() {
    doTest();
  }

  public void testClassPrivate() {
    doTest();
  }

  public void testClassPrivateNotInherited() {
    doTest();
  }

  public void testClassPrivateNotPublic() {
    doTest();
  }

  public void testTwoUnderscores() {
    doTest();
  }

  public void testOneUnderscore() {
    final String testName = "completion/" + getTestName(true);
    myFixture.configureByFile(testName + ".py");
    myFixture.completeBasic();
    myFixture.type('\n');
    myFixture.checkResultByFile(testName + ".after.py");
  }

  public void testKwParamsInCodeUsage() { //PY-1002
    doTest();
  }

  public void testKwParamsInCodeGetUsage() { //PY-1002
    doTest();
  }

  public void testSuperInitKwParamsNotOnlySelfAndKwArgs() { //PY-1050
    doTest();
  }

  public void testSuperInitKwParamsNoCompletion() {
    doTest();
  }

  public void testIsInstance() {
    doTest();
  }

  public void testIsInstanceAssert() {
    doTest();
  }

  public void testIsInstanceTuple() {
    doTest();
  }

  public void testPropertyParens() {  // PY-1037
    doTest();
  }

  public void testClassNameFromVarName() {
    doTest();
  }

  public void testClassNameFromVarNameChained() {  // PY-5629
    doTest();
  }

  public void testPropertyType() {
    doTest();
  }

  public void testSeenMembers() {  // PY-1181
    final String testName = "completion/" + getTestName(true);
    myFixture.configureByFile(testName + ".py");
    final LookupElement[] elements = myFixture.completeBasic();
    assertNotNull(elements);
    assertEquals(1, elements.length);
    assertEquals("children", elements [0].getLookupString());
  }

  public void testImportModule() {
    final String testName = "completion/" + getTestName(true);
    myFixture.configureByFiles(testName + ".py", "completion/someModule.py");
    myFixture.completeBasic();
    myFixture.checkResultByFile(testName + ".after.py");
  }

  public void testPy255() {
    final String dirname = "completion/";
    final String testName = dirname + "moduleClass";
    myFixture.configureByFiles(testName + ".py", dirname + "__init__.py");
    myFixture.copyDirectoryToProject(dirname + "mymodule", dirname + "mymodule");
    myFixture.completeBasic();
    myFixture.checkResultByFile(testName + ".after.py");
  }

  public void testPy874() {
    final String dirname = "completion/";
    final String testName = dirname + "py874";
    myFixture.configureByFile(testName + ".py");
    myFixture.copyDirectoryToProject(dirname + "root", dirname + "root");
    myFixture.completeBasic();
    myFixture.checkResultByFile(testName + ".after.py");
  }

  public void testClassMethod() {  // PY-833
    doTest();
  }

  public void testStarImport() {
    myFixture.configureByFiles("completion/starImport/starImport.py", "completion/starImport/importSource.py");
    myFixture.completeBasic();
    assertSameElements(myFixture.getLookupElementStrings(), Arrays.asList("my_foo", "my_bar"));
  }

  public void testSlots() {  // PY-1211
    doTest();
  }

  public void testReturnType() {
    doTest();
  }

  public void testWithType() { // PY-4198
    setLanguageLevel(LanguageLevel.PYTHON26);
    doTest();
  }

  public void testChainedCall() {  // PY-1565
    doTest();
  }

  public void testFromImportBinary() {
    myFixture.copyFileToProject("completion/root/binary_mod.pyd");
    myFixture.copyFileToProject("completion/root/binary_mod.so");
    myFixture.configureByFiles("completion/fromImportBinary.py", "completion/root/__init__.py");
    myFixture.completeBasic();
    myFixture.checkResultByFile("completion/fromImportBinary.after.py");
  }

  public void testNonExistingProperty() {  // PY-1748
    doTest();
  }

  public void testImportItself() {  // PY-1895
    myFixture.copyDirectoryToProject("completion/importItself/package1", "package1");
    myFixture.configureFromTempProjectFile("package1/submodule1.py");
    myFixture.completeBasic();
    myFixture.checkResultByFile("completion/importItself.after.py");
  }

  public void testImportedFile() { // PY-1955
    final String dirname = "completion/";
    myFixture.copyDirectoryToProject(dirname + "root", dirname + "root");
    myFixture.configureByFile(dirname + "importedFile.py");
    myFixture.completeBasic();
    myFixture.checkResultByFile(dirname + "importedFile.after.py");
  }

  public void testImportedModule() {  // PY-1956
    final String dirname = "completion/";
    myFixture.copyDirectoryToProject(dirname + "root", dirname + "root");
    myFixture.configureByFile(dirname + "importedModule.py");
    myFixture.completeBasic();
    myFixture.checkResultByFile(dirname + "importedModule.after.py");
  }

  public void testDictKeys() {  // PY-2245
    doTest();
  }

  public void testDictKeys2() { //PY-4181
    doTest();
  }

  public void testDictKeys3() { //PY-5546
    doTest();
  }

  public void testNoParensForDecorator() {  // PY-2210
    doTest();
  }

  private void doTest3K() {
    PythonLanguageLevelPusher.setForcedLanguageLevel(myFixture.getProject(), LanguageLevel.PYTHON30);
    try {
      doTest();
    }
    finally {
      PythonLanguageLevelPusher.setForcedLanguageLevel(myFixture.getProject(), null);
    }
  }

  public void testSuperMethod() {  // PY-170
    doTest();
  }

  public void testLocalVarInDictKey() {  // PY-2558
    doTest();
  }

  public void testDictKeyPrefix() {
    doTest();
  }

  public void testDictKeyPrefix2() {      //PY-3683
    doTest();
  }

  public void testNoIdentifiersInImport() {
    doTest();
  }

  public void testSuperClassAttributes() {
    doTest();
  }

  public void testSuperClassAttributesNoCompletionInFunc() {
    doTest();
  }

  public void testRelativeImport() {  // PY-2816
    myFixture.copyDirectoryToProject("completion/relativeImport", "relativeImport");
    myFixture.configureByFile("relativeImport/pkg/main.py");
    myFixture.completeBasic();
    myFixture.checkResultByFile("completion/relativeImport/pkg/main.after.py");
  }

  public void testRelativeImportNameFromInitPy() {  // PY-2816
    myFixture.copyDirectoryToProject("completion/relativeImport", "relativeImport");
    myFixture.configureByFile("relativeImport/pkg/name.py");
    myFixture.completeBasic();
    myFixture.checkResultByFile("completion/relativeImport/pkg/name.after.py");
  }

  public void testImport() {
    doTest();
  }

  public void testDuplicateImportKeyword() {  // PY-3034
    doMultiFileTest();
  }

  public void testImportInMiddleOfHierarchy() {  // PY-3016
    doMultiFileTest();
  }

  public void testVeryPrivate() {  // PY-3246
    doTest();
  }

  public void testReexportModules() {  // PY-2385
    doMultiFileTest();
  }

  public void testModuleDotPy() {  // PY-5813
    doMultiFileTest();
  }

  public void testHasAttr() {  // PY-4423
    doTest();
  }

  public void testEpydocParamTag() {
    final PyDocumentationSettings settings = PyDocumentationSettings.getInstance(myFixture.getModule());
    settings.setFormat(DocStringFormat.EPYTEXT);
    try {
      doTest();
    }
    finally {
      settings.setFormat(DocStringFormat.PLAIN);
    }
  }

  public void testEpydocTags() {
    final PyDocumentationSettings settings = PyDocumentationSettings.getInstance(myFixture.getModule());
    settings.setFormat(DocStringFormat.EPYTEXT);
    try {
      myFixture.configureByFile("completion/epydocTags.py");
      myFixture.completeBasic();
      final List<String> lookupElementStrings = myFixture.getLookupElementStrings();
      assertNotNull(lookupElementStrings);
      assertTrue(lookupElementStrings.contains("@param"));
    }
    finally {
      settings.setFormat(DocStringFormat.PLAIN);
    }
  }

  public void testEpydocTagsMiddle() {
    final PyDocumentationSettings settings = PyDocumentationSettings.getInstance(myFixture.getModule());
    settings.setFormat(DocStringFormat.EPYTEXT);
    try {
      myFixture.configureByFile("completion/epydocTagsMiddle.py");
      myFixture.completeBasic();
      myFixture.checkResultByFile("completion/epydocTagsMiddle.after.py");
    }
    finally {
      settings.setFormat(DocStringFormat.PLAIN);
    }
  }

  public void testIdentifiersInPlainDocstring() {
    final PyDocumentationSettings settings = PyDocumentationSettings.getInstance(myFixture.getModule());
    settings.setFormat(DocStringFormat.PLAIN);
    myFixture.configureByFile("completion/identifiersInPlainDocstring.py");
    myFixture.completeBasic();
    myFixture.checkResultByFile("completion/identifiersInPlainDocstring.after.py");
  }

  public void testPep328Completion() {  // PY-3409
    myFixture.copyDirectoryToProject("completion/pep328", "pep328");
    myFixture.configureByFile("pep328/package/subpackage1/moduleX.py");
    myFixture.completeBasic();
    myFixture.checkResultByFile("completion/pep328/package/subpackage1/moduleX.after.py");
  }

  public void testImportedSubmoduleCompletion() {  // PY-3227
    myFixture.copyDirectoryToProject("completion/submodules", "submodules");
    myFixture.configureByFile("submodules/foo.py");
    myFixture.completeBasic();
    myFixture.checkResultByFile("completion/submodules/foo.after.py");
  }

  public void testFromImportedModuleCompletion() {  // PY-3595
    myFixture.copyDirectoryToProject("completion/py3595", "");
    myFixture.configureByFile("moduleX.py");
    myFixture.completeBasic();
    myFixture.checkResultByFile("completion/py3595/moduleX.after.py");
  }

  public void testExportedConstants() {  // PY-3658
    myFixture.copyDirectoryToProject("completion/exportedConstants", "");
    myFixture.configureByFile("a.py");
    myFixture.completeBasic();
    myFixture.checkResultByFile("completion/exportedConstants/a.after.py");
  }

  public void testAlias() {  // PY-3672
    doTest();
  }

  public void testDuplicateColon() {  // PY-2652
    doTest();
  }

  public void testMro() {  // PY-3989
    doTest();
  }

  public void testPrivateMemberType() {  // PY-4589
    doTest();
  }

  public void testCompleteBeforeSyntaxError() { // PY-3792
    doTest();
  }

  // PY-4279
  public void testFieldReassignment() {
    doTest();
  }

  public void testSuperInit() {  // PY-5066
    doTest();
  }

  public void testAssignedNearby() {
    doTest();
  }

  public void testDunderAll() {
    doMultiFileTest();
  }

  public void testAsName() {
    doMultiFileTest();
  }

  public void testKeywordArgumentsForImplicitCall() {
    doTest();
  }

  public void testTypeMembers() {  // PY-5311
    assertFalse(doTestByText("a = 'string'\n" +
                             "a.<caret>").contains("mro"));
  }

  private List<String> doTestByText(String text) {
    myFixture.configureByText(PythonFileType.INSTANCE, text);
    myFixture.completeBasic();
    return myFixture.getLookupElementStrings();
  }

  public void testDunderAllReference() {  // PY-5502
    doTest();
  }

  public void testDunderAllReferenceImport() {  // PY-6306
    doTest();
  }

  public void testOldStyleClassAttributes() {
    doTest();
  }

  // PY-5821
  public void testGlobalName() {
    doTest();
  }

  // PY-6037
  public void testExceptName() {
    doTest();
  }

  public void testQualifiedAssignment() {  // PY-6121
    doTest();
  }

  public void testRelativeImportExcludeToplevel() {  // PY-6304
    setLanguageLevel(LanguageLevel.PYTHON27);
    try {
      myFixture.copyDirectoryToProject("completion/relativeImportExcludeToplevel", "");
      myFixture.configureByFile("pack/subpack/modX.py");
      myFixture.completeBasic();
      final List<String> lookupElementStrings = myFixture.getLookupElementStrings();
      assertNotNull(lookupElementStrings);
      assertFalse(lookupElementStrings.contains("sys"));
    }
    finally {
      setLanguageLevel(null);
    }
  }

  // PY-2813
  public void testFromNamespacePackageImport() {
    doMultiFileTest();
  }

  // PY-6829
  public void testFakeNameInQualifiedReference() {
    doTest();
  }

  // PY-6603
  public void testNoInitForSubmodules() {
    doMultiFileTest();
  }

  public void testUnknownNewReturnType() {  // PY-6671
    doTest();
  }

  public void testDunderClass() {  // PY-7327
    doTest();
  }

  public void testArgs() {  // PY-7208
    doTestByText("def foo(*<caret>)");
    myFixture.checkResult("def foo(*args)");
  }

  public void testKwArgs() {  // PY-7208
    doTestByText("def foo(**<caret>)");
    myFixture.checkResult("def foo(**kwargs)");
  }

  public void testLocalImportedModule() {  // PY-3668
    myFixture.copyDirectoryToProject("completion/py3668", "");
    myFixture.configureByFile("py3668.py");
    myFixture.completeBasic();
    myFixture.checkResultByFile("completion/py3668/py3668.after.py");
  }

  public void testDuplicateDunderAll() {  // PY-6483
    doTestByText("VAR = 1\nVAR = 2\n__all__ = ['<caret>']");
    myFixture.checkResult("VAR = 1\n" +
                          "VAR = 2\n" +
                          "__all__ = ['VAR']");
  }

  // PY-7805
  public void testNoUnderscoredBuiltin() {
    doTest();
  }

  public void testParameterFromUsages() {
    doTest();
  }

  // PY-1219
  public void testReCompileMatch() {
    doTest();
  }

  public void testReturnTypeOfCallFromUsages() {
    final List<String> results = doTestByText("def f(x):\n" +
                                              "    return x\n" +
                                              "\n" +
                                              "f('foo').<caret>\n");
    assertTrue(results.contains("lower"));
  }

  public void testOverwriteEqualsSign() {  // PY-1337
    doTestByText("def foo(school=None, kiga=None): pass\n" +
                 "foo(<caret>school=None)");
    myFixture.type("sch");
    myFixture.finishLookup(Lookup.REPLACE_SELECT_CHAR);
    myFixture.checkResult("def foo(school=None, kiga=None): pass\n" +
                          "foo(school=None)");
  }

  public void testOverwriteBracket() {  // PY-6095
    doTestByText("bar = {'a': '1'}\n" +
                 "print ba<caret>['a']");
    myFixture.finishLookup(Lookup.REPLACE_SELECT_CHAR);
    myFixture.checkResult("bar = {'a': '1'}\n" +
                          "print bar<caret>['a']");
  }
}
