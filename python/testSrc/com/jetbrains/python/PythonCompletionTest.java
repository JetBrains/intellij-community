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
package com.jetbrains.python;

import com.google.common.collect.Lists;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.completion.impl.CamelHumpMatcher;
import com.intellij.codeInsight.lookup.AutoCompletionPolicy;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.testFramework.PsiTestUtil;
import com.jetbrains.python.documentation.PyDocumentationSettings;
import com.jetbrains.python.documentation.docstrings.DocStringFormat;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.LanguageLevel;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

public class PythonCompletionTest extends PyTestCase {

  private void doTest() {
    CamelHumpMatcher.forceStartMatching(getTestRootDisposable());
    final String testName = getTestName(true);
    myFixture.configureByFile(testName + ".py");
    myFixture.completeBasic();
    myFixture.checkResultByFile(testName + ".after.py");
  }

  private void doMultiFileTest() {
    myFixture.copyDirectoryToProject(getTestName(true), "");
    myFixture.configureByFile("a.py");
    myFixture.completeBasic();
    myFixture.checkResultByFile(getTestName(true) + "/a.after.py");
  }

  private List<String> doTestByText(String text) {
    myFixture.configureByText(PythonFileType.INSTANCE, text);
    myFixture.completeBasic();
    return myFixture.getLookupElementStrings();
  }

  @Nullable
  private List<String> doTestByFile() {
    myFixture.configureByFile(getTestName(true) + ".py");
    myFixture.completeBasic();
    return myFixture.getLookupElementStrings();
  }

  @Nullable
  private List<String> doTestSmartByFile() {
    myFixture.configureByFile(getTestName(true) + ".py");
    myFixture.complete(CompletionType.SMART);
    return myFixture.getLookupElementStrings();
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

  // PY-14044
  public void testNamedTupleInitParams() {
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
    final String testName = getTestName(true);
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

  public void testPySixTest() {
    doTest();
  }

  public void testSeenMembers() {  // PY-1181
    final String testName = getTestName(true);
    myFixture.configureByFile(testName + ".py");
    final LookupElement[] elements = myFixture.completeBasic();
    assertNotNull(elements);
    assertEquals(1, elements.length);
    assertEquals("children", elements[0].getLookupString());
  }

  public void testImportModule() {
    final String testName = getTestName(true);
    myFixture.configureByFiles(testName + ".py", "someModule.py");
    myFixture.completeBasic();
    myFixture.checkResultByFile(testName + ".after.py");
  }

  public void testPy255() {
    final String dirName = getTestName(true);
    myFixture.copyDirectoryToProject(dirName, "");
    myFixture.configureByFiles("moduleClass.py");
    myFixture.completeBasic();
    myFixture.checkResultByFile(dirName + "/moduleClass.after.py");
  }

  public void testPy874() {
    final String testName = "py874";
    myFixture.configureByFile(testName + ".py");
    myFixture.copyDirectoryToProject("root", "root");
    myFixture.completeBasic();
    myFixture.checkResultByFile(testName + ".after.py");
  }

  public void testClassMethod() {  // PY-833
    doTest();
  }

  public void testStarImport() {
    myFixture.configureByFiles("starImport/starImport.py", "starImport/importSource.py");
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
    myFixture.copyFileToProject("root/binary_mod.pyd");
    myFixture.copyFileToProject("root/binary_mod.so");
    myFixture.configureByFiles("fromImportBinary.py", "root/__init__.py");
    myFixture.completeBasic();
    myFixture.checkResultByFile("fromImportBinary.after.py");
  }

  public void testNonExistingProperty() {  // PY-1748
    doTest();
  }

  public void testImportItself() {  // PY-1895
    myFixture.copyDirectoryToProject("importItself/package1", "package1");
    myFixture.configureFromTempProjectFile("package1/submodule1.py");
    myFixture.completeBasic();
    myFixture.checkResultByFile("importItself.after.py");
  }

  public void testImportedFile() { // PY-1955
    myFixture.copyDirectoryToProject("root", "root");
    myFixture.configureByFile("importedFile.py");
    myFixture.completeBasic();
    myFixture.checkResultByFile("importedFile.after.py");
  }

  public void testImportedModule() {  // PY-1956
    myFixture.copyDirectoryToProject("root", "root");
    myFixture.configureByFile("importedModule.py");
    myFixture.completeBasic();
    myFixture.checkResultByFile("importedModule.after.py");
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
    myFixture.copyDirectoryToProject("relativeImport", "relativeImport");
    myFixture.configureByFile("relativeImport/pkg/main.py");
    myFixture.completeBasic();
    myFixture.checkResultByFile("relativeImport/pkg/main.after.py");
  }

  public void testRelativeImportNameFromInitPy() {  // PY-2816
    myFixture.copyDirectoryToProject("relativeImport", "relativeImport");
    myFixture.configureByFile("relativeImport/pkg/name.py");
    myFixture.completeBasic();
    myFixture.checkResultByFile("relativeImport/pkg/name.after.py");
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
      myFixture.configureByFile("epydocTags.py");
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
      myFixture.configureByFile("epydocTagsMiddle.py");
      myFixture.completeBasic();
      myFixture.checkResultByFile("epydocTagsMiddle.after.py");
    }
    finally {
      settings.setFormat(DocStringFormat.PLAIN);
    }
  }

  public void testIdentifiersInPlainDocstring() {
    final PyDocumentationSettings settings = PyDocumentationSettings.getInstance(myFixture.getModule());
    settings.setFormat(DocStringFormat.PLAIN);
    myFixture.configureByFile("identifiersInPlainDocstring.py");
    final LookupElement[] elements = myFixture.completeBasic();
    assertNotNull(elements);
    assertContainsElements(Lists.newArrayList(elements),
                           LookupElementBuilder.create("bar").withAutoCompletionPolicy(AutoCompletionPolicy.NEVER_AUTOCOMPLETE));
  }

  // PY-16877
  public void testSectionNamesInGoogleDocstring() {
    runWithDocStringFormat(DocStringFormat.GOOGLE, () -> {
      final List<String> variants = doTestByFile();
      assertNotNull(variants);
      assertContainsElements(variants, "Args", "Keyword Args", "Returns");
      assertDoesntContain(variants, "Parameters", "Return", "Yield");
    });
  }

  // PY-17023
  public void testSectionNamesInNumpyDocstrings() {
    runWithDocStringFormat(DocStringFormat.NUMPY, () -> {
      final List<String> variants = doTestByFile();
      assertNotNull(variants);
      assertContainsElements(variants, "Parameters", "Other Parameters", "Returns");
      assertDoesntContain(variants, "Args", "Return", "Yield");
    });
  }

  // PY-16991
  public void testSecondSectionNameInGoogleDocstring() {
    runWithDocStringFormat(DocStringFormat.GOOGLE, () -> doTest());
  }

  // PY-16877
  public void testTwoWordsSectionNameInGoogleDocstring() throws Exception {
    runWithDocStringFormat(DocStringFormat.GOOGLE, () -> doTest());
  }

  // PY-16870
  public void testParamNameInGoogleDocstring() {
    runWithDocStringFormat(DocStringFormat.GOOGLE, () -> {
      final List<String> variants = doTestByFile();
      assertNotNull(variants);
      assertSameElements(variants, "param1", "param2");
    });
  }

  // PY-16870
  public void testOverrideParamNameInGoogleDocstring() {
    runWithDocStringFormat(DocStringFormat.GOOGLE, () -> {
      final List<String> variants = doTestByFile();
      assertNotNull(variants);
      assertSameElements(variants, "param2");
    });
  }

  // PY-16870
  public void testOverrideParamNameInRestDocstring() {
    runWithDocStringFormat(DocStringFormat.REST, () -> {
      final List<String> variants = doTestByFile();
      assertNotNull(variants);
      assertSameElements(variants, "param2");
    });
  }

  // PY-16870, PY-16972
  public void testClassNameInDocstring() {
    runWithDocStringFormat(DocStringFormat.EPYTEXT, () -> doTest());
  }

  // PY-17002
  public void testParamTypeInGoogleDocstringWithoutClosingParenthesis() {
    runWithDocStringFormat(DocStringFormat.GOOGLE, () -> {
      final List<String> variants = doTestByFile();
      assertNotNull(variants);
      assertSameElements(variants, "str", "basestring");
    });
  }

  // PY-17635
  public void testParamNameInTypeDeclarationInRestDocstring() {
    runWithDocStringFormat(DocStringFormat.REST, () -> {
      final List<String> variants = doTestByFile();
      assertNotNull(variants);
      assertContainsElements(variants, "foo");
    });
  }

  public void testPep328Completion() {  // PY-3409
    myFixture.copyDirectoryToProject("pep328", "pep328");
    myFixture.configureByFile("pep328/package/subpackage1/moduleX.py");
    myFixture.completeBasic();
    myFixture.checkResultByFile("pep328/package/subpackage1/moduleX.after.py");
  }

  public void testImportedSubmoduleCompletion() {  // PY-3227
    myFixture.copyDirectoryToProject("submodules", "submodules");
    myFixture.configureByFile("submodules/foo.py");
    myFixture.completeBasic();
    myFixture.checkResultByFile("submodules/foo.after.py");
  }

  public void testFromImportedModuleCompletion() {  // PY-3595
    myFixture.copyDirectoryToProject("py3595", "");
    myFixture.configureByFile("moduleX.py");
    myFixture.completeBasic();
    myFixture.checkResultByFile("py3595/moduleX.after.py");
  }

  public void testExportedConstants() {  // PY-3658
    myFixture.copyDirectoryToProject("exportedConstants", "");
    myFixture.configureByFile("a.py");
    myFixture.completeBasic();
    myFixture.checkResultByFile("exportedConstants/a.after.py");
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
    runWithLanguageLevel(LanguageLevel.PYTHON27, () -> {
      myFixture.copyDirectoryToProject("relativeImportExcludeToplevel", "");
      myFixture.configureByFile("pack/subpack/modX.py");
      myFixture.completeBasic();
      assertNull(myFixture.getLookupElementStrings());
      myFixture.checkResult("from ...subpack import");
    });
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
    myFixture.copyDirectoryToProject("py3668", "");
    myFixture.configureByFile("py3668.py");
    myFixture.completeBasic();
    myFixture.checkResultByFile("py3668/py3668.after.py");
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

  // PY-1860
  public void testDunderMetaClass() {
    doTestByText("class C(object):\n" +
                 "    __meta<caret>\n");
    myFixture.checkResult("class C(object):\n" +
                          "    __metaclass__ = \n");
  }

  // PY-13140
  public void testModulePrivateNamesCompletedInsideImport() {
    myFixture.copyDirectoryToProject(getTestName(true), "");
    myFixture.configureByFile("a.py");
    myFixture.completeBasic();
    List<String> suggested = myFixture.getLookupElementStrings();
    assertNotNull(suggested);
    assertContainsElements(suggested, "normal_name", "_private_name", "__magic_name__");
  }

  // PY-4073
  public void testFunctionSpecialAttributes() {
    runWithLanguageLevel(LanguageLevel.PYTHON27, () -> {
      List<String> suggested = doTestByText("def func(): pass; func.func_<caret>");
      assertNotNull(suggested);
      assertContainsElements(suggested, PyNames.LEGACY_FUNCTION_SPECIAL_ATTRIBUTES);

      suggested = doTestByText("def func(): pass; func.__<caret>");
      assertNotNull(suggested);
      assertContainsElements(suggested, PyNames.FUNCTION_SPECIAL_ATTRIBUTES);
      assertDoesntContain(suggested, PyNames.PY3_ONLY_FUNCTION_SPECIAL_ATTRIBUTES);
    });
  }

  // PY-9342
  public void testBoundMethodSpecialAttributes() {
    List<String>  suggested = doTestByText("{}.update.im_<caret>");
    assertNotNull(suggested);
    assertContainsElements(suggested, PyNames.LEGACY_METHOD_SPECIAL_ATTRIBUTES);

    suggested = doTestByText("{}.update.__<caret>");
    assertNotNull(suggested);
    assertContainsElements(suggested, PyNames.METHOD_SPECIAL_ATTRIBUTES);
    assertDoesntContain(suggested, PyNames.FUNCTION_SPECIAL_ATTRIBUTES);
  }

  // PY-9342
  public void testWeakQualifierBoundMethodAttributes() {
    assertUnderscoredMethodSpecialAttributesSuggested();
  }

  private void assertUnderscoredMethodSpecialAttributesSuggested() {
    final List<String> suggested = doTestByFile();
    assertNotNull(suggested);
    assertContainsElements(suggested, PyNames.METHOD_SPECIAL_ATTRIBUTES);
    assertDoesntContain(suggested, PyNames.FUNCTION_SPECIAL_ATTRIBUTES);
  }

  // PY-9342
  public void testUnboundMethodSpecialAttributes() {
    runWithLanguageLevel(LanguageLevel.PYTHON27, () -> assertUnderscoredMethodSpecialAttributesSuggested());
    runWithLanguageLevel(LanguageLevel.PYTHON32, () -> assertUnderscoredFunctionAttributesSuggested());
  }

  // PY-9342
  public void testStaticMethodSpecialAttributes() {
    assertUnderscoredFunctionAttributesSuggested();
  }

  // PY-9342
  public void testLambdaSpecialAttributes() {
    assertUnderscoredFunctionAttributesSuggested();
  }

  // PY-9342
  public void testReassignedMethodSpecialAttributes() {
    assertUnderscoredMethodSpecialAttributesSuggested();
  }

  // PY-5833
  public void testPassedNamedTupleAttributes() {
    doTest();
  }

  private void assertUnderscoredFunctionAttributesSuggested() {
    final List<String> suggested = doTestByFile();
    assertNotNull(suggested);
    assertContainsElements(suggested, PyNames.FUNCTION_SPECIAL_ATTRIBUTES);
    assertDoesntContain(suggested, PyNames.METHOD_SPECIAL_ATTRIBUTES);
  }

  public void testSmartFromUsedMethodsOfString() {
    final List<String> suggested = doTestSmartByFile();
    assertNotNull(suggested);
    // Remove duplicates for assertContainsElements(), "append" comes from bytearray
    assertContainsElements(new HashSet<>(suggested), "lower", "capitalize", "join", "append");
  }

  public void testSmartFromUsedAttributesOfClass() {
    final List<String> suggested = doTestSmartByFile();
    assertNotNull(suggested);
    assertContainsElements(suggested, "other_method", "name", "unique_method");
  }

  // PY-14388
  public void testAttributeOfIndirectlyImportedPackage() {
    doMultiFileTest();
  }

  // PY-14387
  public void testSubmoduleOfIndirectlyImportedPackage() {
    myFixture.copyDirectoryToProject(getTestName(true), "");
    myFixture.configureByFile("a.py");
    myFixture.completeBasic();
    final List<String> suggested = myFixture.getLookupElementStrings();
    assertNotNull(suggested);
    assertSameElements(suggested, "VAR", "subpkg1");
  }

  // PY-14519
  public void testOsPath() {
    myFixture.copyDirectoryToProject(getTestName(true), "");
    myFixture.configureByFile("a.py");
    myFixture.completeBasic();
    final List<String> suggested = myFixture.getLookupElementStrings();
    assertNotNull(suggested);
    assertContainsElements(suggested, "path");
  }

  // PY-14331
  public void testExcludedTopLevelPackage() {
    myFixture.copyDirectoryToProject(getTestName(true), "");
    myFixture.configureByFile("a.py");
    PsiTestUtil.addExcludedRoot(myFixture.getModule(), myFixture.findFileInTempDir("pkg1"));
    final LookupElement[] variants = myFixture.completeBasic();
    assertNotNull(variants);
    assertEmpty(variants);
  }

  // PY-14331
  public void testExcludedSubPackage() {
    myFixture.copyDirectoryToProject(getTestName(true), "");
    myFixture.configureByFile("a.py");
    PsiTestUtil.addExcludedRoot(myFixture.getModule(), myFixture.findFileInTempDir("pkg1/subpkg1"));
    final LookupElement[] variants = myFixture.completeBasic();
    assertNotNull(variants);
    assertEmpty(variants);
  }

  // PY-15119
  public void testRelativeFromImportWhitespacesAfterDot() {
    myFixture.copyDirectoryToProject(getTestName(true), "");
    myFixture.configureByFile("pkg/subpkg1/a.py");
    myFixture.completeBasic();
    assertSameElements(myFixture.getLookupElementStrings(), "import", "subpkg1", "subpkg2", "m");
  }

  // PY-15197
  public void testKeywordArgumentEqualsSignSurroundedWithSpaces() {
    getPythonCodeStyleSettings().SPACE_AROUND_EQ_IN_KEYWORD_ARGUMENT = true;
    doTest();
  }

  public void testStructuralType() {
    doTest();
  }

  // PY-11214
  public void testNext() {
    doTest();
  }

  //PY-3077
  public void testFormatString() {
    doTest();
  }

  //PY-3077
  public void testFormatFuncArgument() {
    doTest();
  }

  //PY-3077
  public void testFormatStringFromStarArg() {
    doTest();
  }

  //PY-3077
  public void testFormatStringOutsideBraces() {
    doTest();
  }

  //PY-3077
  public void testFormatStringFromRef() {
    doTest();
  }

  // PY-17437
  public void testStrFormat() {
    doTest();
  }

  public void testProtectedClassNames() {
    doTest();
  }

  public void testProtectedClassNameNoPrefix() {
    final List<String> variants = doTestByFile();
    assertNotNull(variants);
    assertDoesntContain(variants, "_foo(self)");
  }

  // PY-12425
  public void testInstanceFromDefinedCallAttr() {
    doTest();
  }

  // PY-12425
  public void testInstanceFromFunctionAssignedToCallAttr() {
    doTest();
  }

  // PY-12425
  public void testInstanceFromCallableAssignedToCallAttr() {
    doTest();
  }

  // PY-12425
  public void testInstanceFromInheritedCallAttr() {
    doTest();
  }

  // PY-12425
  public void testInstanceFromProvidedCallAttr() {
    doMultiFileTest();
  }

  // PY-18684
  public void testRPowSignature() {
    doTest();
  }

  // PY-20017
  public void testAncestorHasDunderNewMethod() {
    doTest();
  }

  @Override
  protected String getTestDataPath() {
    return super.getTestDataPath() + "/completion";
  }
}
