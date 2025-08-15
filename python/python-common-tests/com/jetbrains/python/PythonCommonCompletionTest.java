package com.jetbrains.python;

import com.google.common.collect.Lists;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.completion.impl.CamelHumpMatcher;
import com.intellij.codeInsight.lookup.AutoCompletionPolicy;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.codeInsight.PyCodeInsightSettings;
import com.jetbrains.python.codeInsight.completion.PyModuleNameCompletionContributor;
import com.jetbrains.python.codeInsight.typing.PyTypeShed;
import com.jetbrains.python.documentation.docstrings.DocStringFormat;
import com.jetbrains.python.fixture.PythonCommonTestCase;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.sdk.PythonSdkUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public abstract class PythonCommonCompletionTest extends PythonCommonTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.setTestDataPath(getTestDataPath());
    PyModuleNameCompletionContributor.ENABLED = false;
  }

  protected void doTest() {
    CamelHumpMatcher.forceStartMatching(myFixture.getTestRootDisposable());
    final String testName = getTestName(true);
    myFixture.configureByFile(testName + ".py");
    myFixture.completeBasic();
    myFixture.checkResultByFile(testName + ".after.py");
  }

  private void doMultiFileTest() {
    doMultiFileTest(CompletionType.BASIC, 1);
  }

  private void doMultiFileTest(CompletionType completionType, int invocationCount) {
    myFixture.copyDirectoryToProject(getTestName(true), "");
    myFixture.configureByFile("a.py");
    @NotNull LookupElement[] variants = myFixture.complete(completionType, invocationCount);
    myFixture.checkResultByFile(getTestName(true) + "/a.after.py");
  }

  private void doMultiFileTestAssertSameOrderedElements(String... variants) {
    assertOrderedEquals(doMultiFileTestCompletionVariants(), variants);
  }

  @NotNull
  private List<String> doMultiFileTestCompletionVariants() {
    myFixture.copyDirectoryToProject(getTestName(true), "");
    myFixture.configureByFile("a.py");
    myFixture.completeBasic();
    final List<String> suggested = myFixture.getLookupElementStrings();
    assertNotNull(suggested);
    return suggested;
  }

  @Nullable
  private List<String> doTestByText(@NotNull String text) {
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

  // PY-37755 PY-2700
  public void testGlobalAndNonlocalMethod() {
    runWithLanguageLevel(LanguageLevel.PYTHON34, this::doTest);
  }

  public void testStarImport() {
    runWithImportableNamesInBasicCompletionDisabled(() -> {
      myFixture.configureByFiles("starImport/starImport.py", "starImport/importSource.py");
      myFixture.completeBasic();
      assertSameElements(myFixture.getLookupElementStrings(), Arrays.asList("my_foo", "my_bar"));
    });
  }

  // PY-1211, PY-29232
  public void testSlots() {
    final String testName = getTestName(true);
    myFixture.configureByFile(testName + ".py");
    myFixture.completeBasicAllCarets(null);
    myFixture.checkResultByFile(testName + ".after.py");
  }

  // PY-29231
  public void testSlotsAsAllowedNames() {
    final String testName = getTestName(true);
    myFixture.configureByFile(testName + ".py");
    myFixture.completeBasicAllCarets(null);
    myFixture.checkResultByFile(testName + ".after.py");
  }

  public void testReturnType() {
    doTest();
  }

  public void testWithType() { // PY-4198
    runWithLanguageLevel(LanguageLevel.PYTHON26, this::doTest);
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
    myFixture.configureByFile("dictKeys.py");
    myFixture.completeBasic();
    assertSameElements(myFixture.getLookupElementStrings(), "'xyz'");
    myFixture.type('\n');
    myFixture.checkResultByFile("dictKeys.after.py");
  }

  public void testDictKeys2() { //PY-4181
    doTest();
  }

  public void testDictKeys3() { //PY-5546
    doTest();
  }

  // PY-52502
  public void testExpressionDictKey() {
    doTest();
  }

  // PY-42738
  public void testDictLiteralValueAccessWithDoubleQuotes() {
    final String text = """
    d={ "key1": 222, 'key2': 333, 22: True, False: 22 }
    d["<caret>"]""";
    assertSameElements(doTestByText(text), "key1", "key2");
  }

  // PY-42738
  public void testDictLiteralValueAccessWithSingleQuotes() {
    final String text = """
    d={ "key1": 222, 'key2': 333, 22: True, False: 22 }
    d['<caret>']""";
    assertSameElements(doTestByText(text), "key1", "key2");
  }

  // PY-42738
  public void testDictLiteralValueAccessWithoutQuotes() {
    final String text = """
    d={ "key1": 222, 'key2': 333, 22: True, False: 22 }
    d[<caret>]""";
    List<String> suggested = doTestByText(text);
    assertContainsElements(suggested, "\"key1\"", "'key2'");
  }

  // PY-42738
  public void testDictConstructorValueAccessWithDoubleQuotes() {
    final String text = """
    d=dict(aaa=222, bbb="val")
    d["<caret>"]""";
    assertSameElements(doTestByText(text), "aaa", "bbb");
  }

  // PY-42738
  public void testDictConstructorValueAccessWithSingleQuotes() {
    final String text = """
    d=dict(aaa=222, bbb="val")
    d['<caret>']""";
    assertSameElements(doTestByText(text), "aaa", "bbb");
  }

  // PY-42738
  public void testDictConstructorValueAccessWithoutQuotes() {
    final String text = """
    d=dict(aaa=222, bbb="val")
    d[<caret>]""";
    assertContainsElements(doTestByText(text), "\"aaa\"", "\"bbb\"");
  }

  // PY-42738
  public void testDictAssignedValueAccessWithDoubleQuotes() {
    final String text = """
      d={}
      d[30]=True
      d[False]="zzz"
      d["xxx"]=25
      d['yyy']=26
      d["<caret>"]""";
    assertSameElements(doTestByText(text), "xxx", "yyy");
  }

  // PY-42738
  public void testDictAssignedValueAccessWithSingleQuotes() {
    final String text = """
      d={}
      d[30]=True
      d[False]="zzz"
      d["xxx"]=25
      d['yyy']=26
      d['<caret>']""";
    assertSameElements(doTestByText(text), "xxx", "yyy");
  }

  // PY-42738
  public void testDictAssignedValueAccessWithoutQuotes() {
    final String text = """
      d={}
      d[30]=True
      d[False]="zzz"
      d["xxx"]=25
      d['yyy']=26
      d[<caret>]""";
    assertContainsElements(doTestByText(text), "\"xxx\"", "'yyy'");
  }

  public void testNoParensForDecorator() {  // PY-2210
    doTest();
  }

  public void testSuperMethod() {  // PY-170
    doTest();
  }

  public void testSuperMethodWithAnnotation() {
    runWithLanguageLevel(LanguageLevel.getLatest(), this::doTest);
  }

  // PY-45588
  public void testSuperMethodWithAnnotationInsertingImports() {
    runWithLanguageLevel(LanguageLevel.getLatest(), this::doMultiFileTest);
  }

  public void testSuperMethodWithCommentAnnotation() {
    doTest();
  }

  // PY-53200
  public void testSuperMethodWithExistingParameterList() {
    doTest();
  }

  // PY-34493
  public void testSuperMethodAnnotationsNotCopiedFromPyiStub() {
    doMultiFileTest();
  }

  // PY-34493
  public void testSuperMethodAnnotationsCopiedFromThirdPartyLibrary() {
    runWithLanguageLevel(LanguageLevel.getLatest(), () -> {
      runWithAdditionalClassEntryInSdkRoots(getTestName(true) + "/lib", () -> {
        myFixture.copyDirectoryToProject(getTestName(true) + "/src", "");
        myFixture.configureByFile("a.py");
        myFixture.completeBasic();
        myFixture.checkResultByFile(getTestName(true) + "/src/a.after.py");
      });
    });
  }

  // PY-34493
  public void testSuperMethodAnnotationsCopiedFromPyiStubToPyiStub() {
    runWithLanguageLevel(LanguageLevel.getLatest(), () -> {
      myFixture.copyDirectoryToProject(getTestName(true), "");
      myFixture.configureByFile("a.pyi");
      myFixture.complete(CompletionType.BASIC, 1);
      myFixture.checkResultByFile(getTestName(true) + "/a.after.pyi");
    });
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

  public void testIdentifiersInPlainDocstring() {
    runWithDocStringFormat(DocStringFormat.PLAIN, () -> {
      myFixture.configureByFile("identifiersInPlainDocstring.py");
      final LookupElement[] elements = myFixture.completeBasic();
      assertNotNull(elements);
      assertContainsElements(Lists.newArrayList(elements),
                             LookupElementBuilder.create("bar").withAutoCompletionPolicy(AutoCompletionPolicy.NEVER_AUTOCOMPLETE));
    });
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
    runWithDocStringFormat(DocStringFormat.GOOGLE, this::doTest);
  }

  // PY-16877
  public void testTwoWordsSectionNameInGoogleDocstring() {
    runWithDocStringFormat(DocStringFormat.GOOGLE, this::doTest);
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
    runWithDocStringFormat(DocStringFormat.REST, this::doTest);
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

  public void testDunderAllClassReference() {  // PY-54167
    myFixture.copyDirectoryToProject(getTestName(true), "");
    myFixture.configureByFile("__init__.py");
    myFixture.complete(CompletionType.BASIC, 2);
    myFixture.checkResultByFile(getTestName(true) + "/__init__.after.py");
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

  // PY-32268
  public void testFromLocalNamespacePackageInFromImport() {
    doTestImportFromLocalPython3NamespacePackage();
  }

  // PY-32268
  public void testFromLocalNamespacePackageInImportStatement() {
    doTestImportFromLocalPython3NamespacePackage();
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
    myFixture.checkResult("""
                            VAR = 1
                            VAR = 2
                            __all__ = ['VAR']""");
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
    final List<String> results = doTestByText("""
                                                def f(x):
                                                    return x

                                                f('foo').<caret>
                                                """);
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
    doTestByText("""
                   class C(object):
                       __meta<caret>
                   """);
    myFixture.checkResult("""
                            class C(object):
                                __metaclass__ =\s
                            """);
  }

  // PY-13140
  public void testModulePrivateNamesCompletedInsideImport() {
    final List<String> suggested = doMultiFileTestCompletionVariants();
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
    List<String> suggested = doTestByText("""
                                            class C(object):
                                              def f(self): pass

                                            C().f.im_<caret>""");
    assertNotNull(suggested);
    assertContainsElements(suggested, PyNames.LEGACY_METHOD_SPECIAL_ATTRIBUTES);

    suggested = doTestByText("""
                               class C(object):
                                 def f(self): pass

                               C().f.__<caret>""");
    assertNotNull(suggested);
    assertContainsElements(suggested, PyNames.METHOD_SPECIAL_ATTRIBUTES);
    final Set<String> functionAttributes = new HashSet<>(PyNames.FUNCTION_SPECIAL_ATTRIBUTES);
    functionAttributes.removeAll(PyNames.METHOD_SPECIAL_ATTRIBUTES);
    assertDoesntContain(suggested, functionAttributes);
  }

  // PY-9342
  public void testWeakQualifierBoundMethodAttributes() {
    assertUnderscoredMethodSpecialAttributesSuggested();
  }

  private void assertUnderscoredMethodSpecialAttributesSuggested() {
    final List<String> suggested = doTestByFile();
    assertNotNull(suggested);
    assertContainsElements(suggested, PyNames.METHOD_SPECIAL_ATTRIBUTES);
    final Set<String> functionAttributes = new HashSet<>(PyNames.FUNCTION_SPECIAL_ATTRIBUTES);
    functionAttributes.removeAll(PyNames.METHOD_SPECIAL_ATTRIBUTES);
    assertDoesntContain(suggested, functionAttributes);
  }

  // PY-9342
  public void testUnboundMethodSpecialAttributes() {
    runWithLanguageLevel(LanguageLevel.PYTHON27, this::assertUnderscoredMethodSpecialAttributesSuggested);
    runWithLanguageLevel(LanguageLevel.PYTHON34, this::assertUnderscoredFunctionAttributesSuggested);
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
    final Set<String> methodAttributes = new HashSet<>(PyNames.METHOD_SPECIAL_ATTRIBUTES);
    methodAttributes.removeAll(PyNames.FUNCTION_SPECIAL_ATTRIBUTES);
    assertDoesntContain(suggested, methodAttributes);
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
    final List<String> suggested = doMultiFileTestCompletionVariants();
    assertSameElements(suggested, "VAR", "subpkg1");
  }

  //PY-28332
  public void testSubmoduleOfIndirectlyImportedPackage2() {
    final List<String> suggested = doMultiFileTestCompletionVariants();
    assertSameElements(suggested, "VAR", "subpkg1");
  }

  // PY-14331
  public void testExcludedTopLevelPackage() {
    myFixture.copyDirectoryToProject(getTestName(true), "");
    myFixture.configureByFile("a.py");
    myFixture.addExcludedRoot("pkg1");

    final LookupElement[] variants = myFixture.completeBasic();
    assertNotNull(variants);
    assertEmpty(variants);
  }

  // PY-14331
  public void testExcludedSubPackage() {
    myFixture.copyDirectoryToProject(getTestName(true), "");
    myFixture.configureByFile("a.py");
    myFixture.addExcludedRoot("pkg1/subpkg1");
    final LookupElement[] variants = myFixture.completeBasic();
    assertNotNull(variants);
    assertEmpty(variants);
  }

  // PY-15119
  public void testRelativeFromImportWhitespacesAfterDot() {
    myFixture.copyDirectoryToProject(getTestName(true), "");
    myFixture.configureByFile("pkg/subpkg1/a.py");
    myFixture.completeBasic();
    final List<String> variants = myFixture.getLookupElementStrings();
    assertNotNull(variants);
    assertSameElements(variants, "import", "subpkg1", "subpkg2", "m");
  }

  // PY-15197
  public void testKeywordArgumentEqualsSignSurroundedWithSpaces() {
    PythonCodeStyleService.getInstance().setSpaceAroundEqInKeywordArgument(myFixture.getProject(), true);
    doTest();
  }

  public void testStructuralType() {
    doTest();
  }

  // PY-11214
  public void testNext() {
    doTest();
  }

  // PY-3077
  public void testFormatString() {
    doTest();
  }

  // PY-3077
  public void testFormatFuncArgument() {
    doTest();
  }

  // PY-3077
  public void testFormatStringFromStarArg() {
    doTest();
  }

  // PY-3077
  public void testFormatStringOutsideBraces() {
    doTest();
  }

  // PY-3077
  public void testFormatStringFromRef() {
    doTest();
  }

  // PY-3077
  public void testFormatStringWithFormatModifier() {
    doTest();
  }

  // PY-3077
  public void testPercentStringWithDictLiteralArg() {
    doTest();
  }

  // PY-3077
  public void testPercentStringWithDictCallArg() {
    doTest();
  }

  // PY-3077
  public void testPercentStringWithParenDictCallArg() {
    doTest();
  }

  // PY-3077
  public void testPercentStringWithModifiers() {
    doTest();
  }

  // PY-3077
  public void testPercentStringDictLiteralStringKey() {
    doTest();
  }

  // PY-3077
  public void testPercentStringDictCallStringKey() {
    doTest();
  }

  // PY-3077
  public void testPercentStringDictLiteralArgument() {
    doTest();
  }

  // PY-19839
  public void testPercentStringDictRefKeys() {
    final List<String> variants = doTestByFile();
    assertNullOrEmpty(variants);
  }

  // PY-19839
  public void testPercentStringDictFuncKeys() {
    final List<String> variants = doTestByFile();
    assertNullOrEmpty(variants);
  }

  // PY-19839
  public void testPercentStringDictWithZipCall() {
    final List<String> variants = doTestByFile();
    assertNullOrEmpty(variants);
  }

  // PY-19839
  public void testPercentStringDictWithListArg() {
    final List<String> variants = doTestByFile();
    assertNullOrEmpty(variants);
  }

  // PY-19839
  public void testPercentStringDictWithDictLiteralArg() {
    final List<String> variants = doTestByFile();
    assertNullOrEmpty(variants);
  }

  // PY-19839
  public void testPercentStringDictWithPackedDictLiteralArg() {
    final List<String> variants = doTestByFile();
    assertNullOrEmpty(variants);
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

  // PY-18684
  public void testRPowSignature() {
    doTest();
  }

  // PY-20017
  public void testAncestorHasDunderNewMethod() {
    doTest();
  }

  // PY-20279
  public void testImplicitDunderClass() {
    final List<String> inClassMethod = doTestByText("""
                                                      class First:
                                                          def foo(self):
                                                              print(__cl<caret>)""");
    assertNotNull(inClassMethod);
    assertDoesntContain(inClassMethod, PyNames.__CLASS__);

    final List<String> inStaticMethod = doTestByText("""
                                                       class First:
                                                           @staticmethod
                                                           def foo():
                                                               print(__cl<caret>)""");
    assertNotNull(inStaticMethod);
    assertDoesntContain(inStaticMethod, PyNames.__CLASS__);

    final List<String> inClass = doTestByText("class First:\n" +
                                              "    print(__cl<caret>)");
    assertNotNull(inClass);
    assertEmpty(inClass);

    final List<String> inFunction = doTestByText("def abc():\n" +
                                                 "    print(__cl<caret>)");
    assertNotNull(inFunction);
    assertEmpty(inClass);
  }

  // PY-20768
  public void testInitSubclassBuiltinMethod() {
    runWithLanguageLevel(LanguageLevel.PYTHON36,
                         () -> {
                           doTestByText("class Cl(object):\n" +
                                        "  def __init_su<caret>");
                           myFixture.checkResult("class Cl(object):\n" +
                                                 "  def __init_subclass__(cls, **kwargs):");
                         });
  }

  // PY-20768
  public void testSetNameBuiltinMethod() {
    runWithLanguageLevel(LanguageLevel.PYTHON36,
                         () -> {
                           doTestByText("class Cl(object):\n" +
                                        "  def __set_n<caret>");
                           myFixture.checkResult("class Cl(object):\n" +
                                                 "  def __set_name__(self, owner, name):");
                         });
  }

  // PY-20769
  public void testFsPathBuiltinMethod() {
    runWithLanguageLevel(LanguageLevel.PYTHON36,
                         () -> {
                           doTestByText("class Cl(object):\n" +
                                        "  def __fspa<caret>");
                           myFixture.checkResult("class Cl(object):\n" +
                                                 "  def __fspath__(self):");
                         });
  }

  public void testSixAddMetaclass() {
    final List<String> suggested = doTestByText("""
                                                  import six
                                                  class M(type):
                                                      def baz(self):
                                                          pass
                                                  @six.add_metaclass(M)
                                                  class C(object):
                                                      def foo(self):
                                                          C.ba<caret>()""");

    assertNotNull(suggested);
    assertContainsElements(suggested, "baz");
  }

  public void testSixAddMetaclassWithAs() {
    final List<String> suggested = doTestByText("""
                                                  from six import add_metaclass as a_m
                                                  class M(type):
                                                      def baz(self):
                                                          pass
                                                  @a_m(M)
                                                  class C(object):
                                                      def foo(self):
                                                          C.ba<caret>()""");

    assertNotNull(suggested);
    assertContainsElements(suggested, "baz");
  }

  // PY-22570
  public void testNamesReexportedViaStarImport() {
    final List<String> suggested = doMultiFileTestCompletionVariants();
    assertSameElements(suggested, "mod1", "mod2", "foo", "_bar");
  }

  // PY-23150
  public void testHeavyStarPropagation() {
    doMultiFileTest();
    assertSize(802, myFixture.getLookupElements());
  }

  // PY-22828
  public void testNoImportedBuiltinNames() {
    final List<String> suggested = doTestByText("T<caret>\n");
    assertNotNull(suggested);
    assertContainsElements(suggested, "TypeError");
    assertDoesntContain(suggested, "TypeVar");
  }

  // PY-22828
  public void testNoProtectedBuiltinNames() {
    final List<String> suggested = doTestByText("_<caret>\n");
    assertNotNull(suggested);
    assertContainsElements(suggested, "__import__");
    assertDoesntContain(suggested, "_T", "_KT");
  }

  // PY-18246
  public void testTypingNamedTupleCreatedViaCallInstance() {
    final List<String> suggested = doTestByText(
      """
        from typing import NamedTuple
        EmployeeRecord = NamedTuple('EmployeeRecord', [
            ('name', str),
            ('age', int),
            ('title', str),
            ('department', str)
        ])
        e = EmployeeRecord('n', 'a', 't', 'd')
        e.<caret>"""
    );
    assertNotNull(suggested);
    assertContainsElements(suggested, "name", "age", "title", "department");
  }

  // PY-18246
  public void testTypingNamedTupleCreatedViaKwargsCallInstance() {
    final List<String> suggested = doTestByText(
      """
        from typing import NamedTuple
        EmployeeRecord = NamedTuple('EmployeeRecord', name=str, age=int, title=str, department=str)
        e = EmployeeRecord('n', 'a', 't', 'd')
        e.<caret>"""
    );
    assertNotNull(suggested);
    assertContainsElements(suggested, "name", "age", "title", "department");
  }

  // PY-18246
  public void testTypingNamedTupleCreatedViaInheritanceInstance() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON36,
      () -> {
        final List<String> suggested = doTestByText(
          """
            from typing import NamedTuple
            class EmployeeRecord(NamedTuple):
                name: str
                age: int
                title: str
                department: str
            e = EmployeeRecord('n', 'a', 't', 'd')
            e.<caret>"""
        );
        assertNotNull(suggested);
        assertContainsElements(suggested, "name", "age", "title", "department");
      }
    );
  }

  // PY-21519
  public void testTypeComment() {
    final List<String> variants = doTestByFile();
    assertContainsElements(variants, "List", "Union", "Optional");
  }

  public void testIncompleteQualifiedNameClashesWithLocalVariable() {
    final List<String> variants = doTestByFile();
    assertContainsElements(variants, "upper", "split", "__len__");
    assertDoesntContain(variants, "illegal");
  }

  // PY-8132
  public void testOuterCompletionVariantDoesNotOverwriteClosestOne() {
    doTest();
  }

  // PY-15365
  public void testModulesAndPackagesInDunderAll() {
    final List<String> suggested = doMultiFileTestCompletionVariants();
    assertSameElements(suggested, "m1", "m2", "m3", "m4");
  }

  // PY-26978
  public void testTupleParameterNamesNotSuggested() {
    final List<String> variants = doTestByFile();
    assertContainsElements(variants, "baz=");
    assertDoesntContain(variants, "bar=");
  }

  // PY-27146
  public void testPrivateMemberOwnerResolvedToStub() {
    doMultiFileTest();
  }

  // PY-28017
  public void testModuleGetAttrAndDir() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON37,
      () -> {
        final List<String> suggested = doTestByText("def __<caret>");
        assertNotNull(suggested);
        assertContainsElements(suggested, "__getattr__(name)", "__dir__()");
      }
    );
  }

  // PY-27913
  public void testDunderClassGetItem() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON37,
      () -> {
        final List<String> suggested = doTestByText("class A:\n" +
                                                    "    def __<caret>");
        assertNotNull(suggested);
        assertContainsElements(suggested, "__class_getitem__(cls, item)");
      }
    );
  }

  // PY-23632
  public void testMockPatchObject1Py2() {
    final String testName = getTestName(true);

    final VirtualFile libDir = PyTypeShed.INSTANCE.getStubRootForPackage("mock");
    assertNotNull(libDir);

    runWithAdditionalClassEntryInSdkRoots(
      libDir,
      () -> {
        myFixture.configureByFile(testName + "/a.py");
        myFixture.completeBasic();
        myFixture.checkResultByFile(testName + "/a.after.py");
      }
    );
  }

  // PY-23632
  public void testMockPatchObject2Py2() {
    final String testName = getTestName(true);

    final VirtualFile libDir = PyTypeShed.INSTANCE.getStubRootForPackage("mock");
    assertNotNull(libDir);

    runWithAdditionalClassEntryInSdkRoots(
      libDir,
      () -> {
        myFixture.configureByFile(testName + "/a.py");
        myFixture.completeBasic();
        myFixture.checkResultByFile(testName + "/a.after.py");
      }
    );
  }

  // PY-28577
  public void testBuiltinObjectMethodsInNewStyleClass() {
    final List<String> suggested = doTestByText("class A(object):\n" +
                                                "    def __<caret>");
    assertNotNull(suggested);
    assertContainsElements(suggested, "__init__(self)");
  }

  // PY-28461
  public void testImplicitImportsInsidePackage() {
    runWithLanguageLevel(LanguageLevel.PYTHON37,
                         () -> doMultiFileTestAssertSameOrderedElements("bar", "foo", "foo2", "m1", "pkg2", "pkg3"));
  }

  // PY-28461
  public void testImplicitImportsInsidePackagePy2() {
    runWithLanguageLevel(LanguageLevel.PYTHON27,
                         () -> doMultiFileTestAssertSameOrderedElements("bar", "foo", "foo2", "m1", "pkg2", "pkg3"));
  }

  // PY-3674
  public void testUnderscoredItemsOrderModuleImport() {
    doMultiFileTestAssertSameOrderedElements("A", "B", "f", "_A", "_f", "_g", "__A", "__f");
  }

  // PY-3674
  public void testUnderscoredItemsOrderFromModuleImport() {
    doMultiFileTestAssertSameOrderedElements("A", "B", "f", "_A", "_f", "_g", "__A", "__f");
  }

  // PY-17810
  public void testLocalClasses() {
    assertNoVariantsInExtendedCompletion();
  }

  // PY-17810
  public void testEntriesWithNoImportablePath() {
    assertNoVariantsInExtendedCompletion();
  }

  // PY-17810
  public void testDuplicatedEntriesFromMultipleSourceRoots() {
    assertSingleVariantInExtendedCompletionWithSourceRoots();
  }

  // PY-17810
  public void testModuleWithNoImportablePath() {
    assertNoVariantsInExtendedCompletion();
  }

  // PY-17810
  public void testModuleFromMultipleSourceRoots() {
    assertSingleVariantInExtendedCompletionWithSourceRoots();
  }

  // PY-17810
  public void testPackageFromMultipleSourceRoots() {
    assertSingleVariantInExtendedCompletionWithSourceRoots();
  }

  // PY-17810
  public void testFromPackageImport() {
    myFixture.copyDirectoryToProject(getTestName(true), "");
    myFixture.configureByFile("a.py");
    myFixture.complete(CompletionType.BASIC, 2);
    final List<String> suggested = myFixture.getLookupElementStrings();
    assertNotNull(suggested);
    assertSameElements(suggested, "m1", "m2");
  }

  // PY-28989
  public void testModuleFromNamespacePackage() {
    runWithLanguageLevel(LanguageLevel.PYTHON34, this::assertSingleVariantInExtendedCompletion);
  }

  // PY-29158
  public void testModuleStringLiteralCompletion() {
    doMultiFileTest(CompletionType.BASIC, 2);
  }

  // PY-28341
  public void testCompletionForUsedAttribute() {
    doMultiFileTest();
  }

  // PY-28103
  public void testPrintFunctionWithoutFuture() {
    final List<String> suggested = doTestByText("pr<caret>");
    assertNotNull(suggested);
    assertSameElements(suggested, "print", "property", "repr");
  }

  // PY-28103
  public void testPrintFunctionWithFuture() {
    final List<String> suggested = doTestByText("from __future__ import print_function\npr<caret>");
    assertNotNull(suggested);
    assertSameElements(suggested, "print", "print", "print_function", "property", "repr");
  }

  // PY-27148
  public void testNamedTupleSpecial() {
    final List<String> suggested = doTestByText("""
                                                  from collections import namedtuple
                                                  class Cat1(namedtuple("Cat", "name age")):
                                                      pass
                                                  c1 = Cat1("name", 5)
                                                  c1.<caret>""");
    assertNotNull(suggested);
    assertContainsElements(suggested, "_make", "_asdict", "_replace", "_fields");
  }

  // PY-31938
  public void testReassignedDictKeys() {
    final List<String> suggested = doTestByText("""
                                                  foo = {'k1': '1', 'k2': '2'}
                                                  bar = foo
                                                  bar['<caret>']""");
    assertNotNull(suggested);
    assertContainsElements(suggested, "k1", "k2");
  }

  // PY-33254
  public void testMultipartStringPath() {
    doMultiFileTest();
  }

  // PY-33254
  public void testRbStringPath() {
    doMultiFileTest();
  }

  // PY-33254
  public void testKeywordArgumentPatternStringPath() {
    doMultiFileTest();
  }

  // PY-33254
  public void testBuiltinOpenStringPath() {
    doMultiFileTest();
  }

  // PY-33254
  public void testPandasReadCsvStringPath() {
    doMultiFileTest();
  }

  // PY-33254
  public void testAssignmentPatternStringPath() {
    doMultiFileTest();
  }

  // PY-33254
  public void testQualifiedAssignmentPatternStringPath() {
    doMultiFileTest();
  }

  // PY-8302
  public void testUndeclaredFunction() {
    myFixture.configureByFile("uninitialized/fun.py");
    myFixture.completeBasic();
    List<String> suggested = myFixture.getLookupElementStrings();
    assertNotNull(suggested);
    assertDoesntContain(suggested, "foo");
  }

  // PY-8302
  public void testUninitializedVarBefore() {
    myFixture.configureByFile("uninitialized/variable.py");
    myFixture.completeBasic();
    List<String> suggested = myFixture.getLookupElementStrings();
    assertNotNull(suggested);
    assertDoesntContain(suggested, "foo");
  }

  // PY-8302
  public void testUninitializedVarOnSameLine() {
    List<String> suggested = doTestByText("foo = f<caret>");
    assertNotNull(suggested);
    assertDoesntContain(suggested, "foo");
  }

  // PY-8302
  public void testUninitializedVarOnMultiLine() {
    List<String> suggested = doTestByText("foo = \"this is a string\"\\\n" +
                                          "      \"on several lines\" + f<caret>");
    assertNotNull(suggested);
    assertDoesntContain(suggested, "foo");
  }

  // PY-8302
  public void testUndeclaredClass() {
    myFixture.configureByFiles("uninitialized/MyClass.py");
    myFixture.completeBasic();
    List<String> suggested = myFixture.getLookupElementStrings();
    assertNotNull(suggested);
    assertDoesntContain(suggested, "MyClass");
  }

  // PY-8302
  public void testDeclaredClass() {
    List<String> suggested = doTestByText("""
                                            class AClass:
                                                pass

                                            class BClass(A<caret>)""");
    assertNotNull(suggested);
    assertContainsElements(suggested, "AClass");
  }

  // PY-8302
  public void testBeforeImport() {
    runWithImportableNamesInBasicCompletionDisabled(() -> {
      myFixture.configureByFiles("beforeImport/beforeImport.py", "beforeImport/source.py");
      myFixture.completeBasic();
      List<String> suggested = myFixture.getLookupElementStrings();
      assertDoesntContain(suggested, "my_foo", "my_bar");
    });
  }

  // PY-8302
  public void testBeforeImportAs() {
    myFixture.configureByFiles("beforeImport/beforeImportAs.py", "beforeImport/source.py");
    myFixture.completeBasic();
    List<String> suggested = myFixture.getLookupElementStrings();
    assertDoesntContain(suggested, "my_renamed_foo");
  }

  // PY-8302
  public void testBeforeStarImport() {
    runWithImportableNamesInBasicCompletionDisabled(() -> {
      myFixture.configureByFiles("beforeImport/beforeStarImport.py", "beforeImport/source.py");
      myFixture.completeBasic();
      List<String> suggested = myFixture.getLookupElementStrings();
      assertDoesntContain(suggested, "my_foo", "my_bar");
    });
  }

  // PY-8302
  public void testSecondInvocationForClass() {
    myFixture.configureByText(PythonFileType.INSTANCE, "class MyClass(A<caret>)");
    myFixture.complete(CompletionType.BASIC, 2);
    List<String> suggested = myFixture.getLookupElementStrings();
    assertDoesntContain(suggested, "MyClass");
  }

  // PY-8302
  public void testSecondInvocationForFun() {
    myFixture.configureByFile("uninitialized/fun.py");
    myFixture.complete(CompletionType.BASIC, 2);
    List<String> suggested = myFixture.getLookupElementStrings();
    assertDoesntContain(suggested, "foo");
  }

  // PY-8302
  public void testSecondInvocationForVar() {
    myFixture.configureByFile("uninitialized/variable.py");
    myFixture.complete(CompletionType.BASIC, 2);
    List<String> suggested = myFixture.getLookupElementStrings();
    assertDoesntContain(suggested, "foo");
  }

  // PY-11977
  public void testClassStaticMembers() {
    final String text = """
      class MyClass(object):
        def __init__(self):
          self.foo = 42

        def baz(self):
          pass


      MyClass.bar = 42
      MyClass.<caret>""";
    myFixture.configureByText(PythonFileType.INSTANCE, text);
    myFixture.completeBasic();
    List<String> suggested = myFixture.getLookupElementStrings();
    assertContainsElements(suggested, "bar", "baz");
    assertDoesntContain(suggested, "foo");
  }

  // PY-32269
  public void testParamCompletionWithEquals() {
    doTest();
  }

  // PY-36003
  public void testContinueInFinallyBefore38() {
    final String text = """
      for x in []:
          try:
              a = 1
          finally:
              cont""";

    runWithLanguageLevel(
      LanguageLevel.PYTHON37,
      () -> {
        myFixture.configureByText(PythonFileType.INSTANCE, text + "<caret>");
        myFixture.completeBasic();
        myFixture.checkResult(text);
      }
    );
  }

  // PY-36003
  public void testContinueInFinallyAfter38() {
    final String text = """
      for x in []:
          try:
              a = 1
          finally:
              cont""";

    runWithLanguageLevel(
      LanguageLevel.PYTHON38,
      () -> {
        myFixture.configureByText(PythonFileType.INSTANCE, text + "<caret>");
        myFixture.completeBasic();
        myFixture.checkResult(text + "inue");
      }
    );
  }

  // PY-36008
  public void testTypedDictHasDictMethods() {
    final List<String> suggested = doTestByText("""
                                                  from typing import TypedDict
                                                  class A(TypedDict):
                                                      pass
                                                  A().<caret>""");

    assertNotNull(suggested);
    assertContainsElements(suggested, "update", "clear", "pop", "popitem", "setdefault");
  }

  // PY-39703
  public void testTypedDictUsingConstructorKeysCompletion() {
    final String test1 = """
      from typing import TypedDict
      class A(TypedDict):
          x: int
          y: int
      a = A(x=42, y=1)
      b = a['<caret>']""";
    runWithLanguageLevel(
      LanguageLevel.getLatest(),
      () -> assertContainsElements(doTestByText(test1), "x", "y")
    );
  }

  // PY-39703
  public void testTypedDictInTypedDictKeys() {
    final String test2 = """
      from typing import TypedDict, Mapping
      class Movie(TypedDict):
          name: str
          year: int
      class A(TypedDict):
          film: Movie
          genre: str
      def get_back(m: A):
         a = m['film']['<caret>']""";
    runWithLanguageLevel(
      LanguageLevel.getLatest(),
      () -> assertContainsElements(doTestByText(test2), "name", "year")
    );
  }

  // PY-39703
  public void testTypedDictKeysUsingTypeAnnotation() {
    final String test3 = """
      from typing import TypedDict
      class Movie(TypedDict, total=False):
          name: str
          year: int
      m: Movie = {}
      m['<caret>']""";
    runWithLanguageLevel(
      LanguageLevel.getLatest(),
      () -> assertContainsElements(doTestByText(test3), "name", "year")
    );
  }

  // PY-39703
  public void testTypedDictWithWrongKey() {
    final String test4 = """
      from typing import TypedDict
      class Movie(TypedDict, total=False):
          name: str
          year: int
      m: Movie = {'name': 'Alien', 'year': 1979}
      m['wrong_key'] = 1
      m['<caret>']""";
    runWithLanguageLevel(
      LanguageLevel.getLatest(),
      () -> {
        List<String> lookupElementStrings = doTestByText(test4);

        assertContainsElements(lookupElementStrings, "name", "year");
        assertDoesntContain(lookupElementStrings, "wrong_key");
      }
    );
  }

  // PY-42637
  public void testTypedDictValueAccessWithDoubleQuotes() {
    final String text = """
      from typing import TypedDict
      class VehicleTypedDict(TypedDict):
          id: int
          vin: str
          zip: str
          make: str
          trim: str
      def get_something(vehicle: VehicleTypedDict):
          return vehicle["<caret>"]""";
    runWithLanguageLevel(
      LanguageLevel.getLatest(),
      () -> assertContainsElements(doTestByText(text), "id", "vin", "zip", "make", "trim")
    );
  }

  // PY-42637
  public void testTypedDictValueAccessWithoutQuotes() {
    final String text = """
      from typing import TypedDict
      class VehicleTypedDict(TypedDict):
          id: int
          vin: str
          zip: str
          make: str
          trim: str
      def get_something(vehicle: VehicleTypedDict):
          return vehicle[<caret>]""";
    runWithLanguageLevel(
      LanguageLevel.getLatest(),
      () -> assertContainsElements(doTestByText(text), "\"id\"", "\"vin\"", "\"zip\"", "\"make\"", "\"trim\"")
    );
  }

  // PY-42637
  public void testTypedDictValueAccessWithSingleQuotes() {
    final String text = """
      from typing import TypedDict
      class VehicleTypedDict(TypedDict):
          id: int
          vin: str
          zip: str
          make: str
          trim: str
      def get_something(vehicle: VehicleTypedDict):
          return vehicle['<caret>']""";
    runWithLanguageLevel(
      LanguageLevel.getLatest(),
      () -> assertContainsElements(doTestByText(text), "id", "vin", "zip", "make", "trim")
    );
  }

  // PY-42637
  public void testTypedDictValueAccessHalfTyped() {
    final String text = """
      from typing import TypedDict
      class Point(TypedDict):
          coordinateX: int
          coordinateY: int
          z: int
      def draw(point: Point):
          return point["coo<caret>"]""";
    runWithLanguageLevel(
      LanguageLevel.getLatest(),
      () -> {
        List<String> lookupElementStrings = doTestByText(text);

        assertContainsElements(lookupElementStrings, "coordinateX", "coordinateY");
        assertDoesntContain(lookupElementStrings, "z");
      }
    );
  }

  // PY-36008
  public void testTypedDictDefinition() {
    final List<String> suggested = doTestByText("""
                                                  from typing import TypedDict
                                                  class A(TypedDict, total=<caret>):
                                                  """);

    final List<String> suggestedInAlternativeSyntax = doTestByText("""
                                                                     from typing import TypedDict
                                                                     A = TypedDict('A', {}, total=<caret>):
                                                                     """);

    assertNotNull(suggested);
    assertContainsElements(suggested, "True", "False");

    assertNotNull(suggestedInAlternativeSyntax);
    assertContainsElements(suggestedInAlternativeSyntax, "True", "False");
  }

  // PY-38438
  public void testPythonCompletionRankingForImportKeyword() {
    myFixture.configureByText(PythonFileType.INSTANCE, "im<caret>");
    myFixture.completeBasic();
    List<String> suggested = myFixture.getLookupElementStrings();
    int indImport = suggested.indexOf("import");
    int ind__import__ = suggested.indexOf("__import__");
    assertTrue(indImport != -1);
    assertTrue(ind__import__ != -1);
    assertTrue(indImport < ind__import__);
  }

  // PY-10184
  public void testHasattrSimpleAnd() {
    String[] inList = {"foo", "bar"};
    doTestHasattrContributor(inList, null);
  }

  // PY-10184
  public void testHasattrAndWithNot() {
    String[] inList = {"foo", "baz"};
    String[] notInList = {"bar"};
    doTestHasattrContributor(inList, notInList);
  }

  // PY-10184
  public void testHasattrIfPyramidAndOr() {
    String[] inList = {"foo", "qux", "corge", "bar", "baz", "quux", "quuz"};
    doTestHasattrContributor(inList, null);
  }

  // PY-10184
  public void testHasattrInElseBranch() {
    String[] notInList = {"ajjj"};
    doTestHasattrContributor(null, notInList);
  }

  // PY-10184
  public void testHasattrInElseBranchAfterElif() {
    String[] notInList = {"foo1", "foo2"};
    doTestHasattrContributor(null, notInList);
  }

  // PY-10184
  public void testHasattrInElifBranch() {
    String[] inList = {"foo2"};
    String[] notInList = {"foo1"};
    doTestHasattrContributor(inList, notInList);
  }

  // PY-10184
  public void testHasattrInRightPartOfAnd() {
    String[] inList = {"foo", "bar"};
    doTestHasattrContributor(inList, null);
  }

  // PY-10184
  public void testHasattrInConditionalExpression() {
    String[] inList = {"foo", "bar"};
    doTestHasattrContributor(inList, null);
  }

  // PY-39956
  public void testDuplicatesFromProvider() {
    final String testName = getTestName(true);

    final VirtualFile skeletonsDir =
      StandardFileSystems.local().findFileByPath(getTestDataPath() + "/" + testName + "/" + PythonSdkUtil.SKELETON_DIR_NAME);

    assertNotNull(skeletonsDir);
    runWithAdditionalClassEntryInSdkRoots(
      skeletonsDir,
      () -> assertNull(doTestByText("from itertools import prod<caret>"))
    );
  }

  // PY-38172
  public void testNoPrivateStubElementsInCompletionForCollectionsModule() {
    PsiFile file = myFixture.configureByText(PythonFileType.INSTANCE, """
      import collections
      collections.<caret>
      """);
    myFixture.completeBasic();
    List<String> suggested = myFixture.getLookupElementStrings();
    assertNotEmpty(suggested);
    assertDoesntContain(suggested, "Union", "TypeVar", "Generic", "_S", "_T");
    assertProjectFilesNotParsed(file);
    assertSdkRootsNotParsed(file);
  }

  // PY-38172
  public void testPrivateStubElementsNotSuggestedInPyFiles() {
    doMultiFileTest();
  }

  // PY-38172
  public void testPrivateStubElementsSuggestedInOtherPyiStubs() {
    myFixture.copyDirectoryToProject(getTestName(true), "");
    myFixture.configureByFile("a.pyi");
    myFixture.complete(CompletionType.BASIC, 1);
    myFixture.checkResultByFile(getTestName(true) + "/a.after.pyi");
  }

  // PY-42520
  public void testNoRepeatingNamedArgs() {
    runWithLanguageLevel(
      LanguageLevel.getLatest(),
      () -> {
        List<String> suggested = doTestByText("print(end='.', <caret>");
        assertContainsElements(suggested, "sep=", "file=");
        assertDoesntContain(suggested, "end=");
      }
    );
  }

  // PY-42772
  public void testNoPositionalOnlyArgumentsSuggestion() {
    runWithLanguageLevel(
      LanguageLevel.getLatest(),
      () -> {
        List<String> suggested = doTestByText("""
                                                def foo(argument_1, /, argument_2):
                                                    pass

                                                foo(<caret>)""");
        assertContainsElements(suggested, "argument_2=");
        assertDoesntContain(suggested, "argument_1=");
      }
    );
  }

  // PY-25832
  public void testTypeVarBoundAttributes() {
    runWithLanguageLevel(LanguageLevel.getLatest(), this::doTest);
  }

  // PY-25832
  public void testTypeVarConstraintsAttributes() {
    runWithLanguageLevel(LanguageLevel.getLatest(), () -> {
      List<String> variants = doTestByFile();
      assertContainsElements(variants, "upper", "bit_length");
    });
  }

  // PY-25832
  public void testTypeVarClassObjectBoundAttributes() {
    runWithLanguageLevel(LanguageLevel.getLatest(), () -> {
      List<String> variants = doTestByFile();
      assertContainsElements(variants, "class_attr");
      assertDoesntContain(variants, "inst_attr");
    });
  }

  // PY-53200
  public void testMethodNamesSuggestedWithoutParameterListIfItIsAlreadyExist() {
    final List<String> suggested = doMultiFileTestCompletionVariants();
    assertContainsElements(suggested, "something_a", "something_b");
  }

  // PY-17627
  public void testClassAttributeDefinedInClassMethod() {
    doTest();
  }

  // PY-34617
  public void testVersionCheckAtFileLevel() {
    runWithLanguageLevel(LanguageLevel.PYTHON27, () -> {
      List<String> suggested = doTestByFile();
      assertContainsElements(suggested, "attr0", "attr3", "f0", "f3", "MyClass0", "MyClass3");
      assertDoesntContain(suggested, "attr1", "attr2", "f1", "f2", "MyClass1", "MyClass2");
    });
  }

  // PY-34617
  public void testVersionCheckAtClassLevel() {
    runWithLanguageLevel(LanguageLevel.PYTHON25, () -> {
      List<String> suggested = doTestByFile();
      assertContainsElements(suggested, "attr0", "attr2", "f0", "f2", "MyClass0", "MyClass2");
      assertDoesntContain(suggested, "attr1", "attr3", "f1", "f3", "MyClass1", "MyClass3");
    });
  }

  // PY-34617
  public void testVersionCheckInClassInsideMethod() {
    runWithLanguageLevel(LanguageLevel.PYTHON310, () -> {
      List<String> suggested = doTestByFile();
      assertContainsElements(suggested, "f0", "f1");
      assertDoesntContain(suggested, "f2", "f3");
    });
  }

  private void doTestHasattrContributor(String[] inList, String[] notInList) {
    doTestHasattrContributor("hasattrCompletion/" + getTestName(true) + ".py", inList, notInList);
  }

  private void doTestHasattrContributor(String testFileName, String[] inList, String[] notInList) {
    myFixture.configureByFile(testFileName);
    myFixture.completeBasic();
    List<String> suggested = myFixture.getLookupElementStrings();
    if (inList != null) {
      assertContainsElements(suggested, inList);
    }
    if (notInList != null) {
      assertDoesntContain(suggested, notInList);
    }
  }

  private void assertNoVariantsInExtendedCompletion() {
    myFixture.copyDirectoryToProject(getTestName(true), "");
    myFixture.configureByFile("a.py");
    myFixture.complete(CompletionType.BASIC, 2);
    assertNotNull(myFixture.getLookupElements());
    assertEmpty(myFixture.getLookupElements());
  }

  private void assertSingleVariantInExtendedCompletion() {
    doMultiFileTest(CompletionType.BASIC, 2);
    assertNull(myFixture.getLookupElements());
  }

  private void assertSingleVariantInExtendedCompletionWithSourceRoots() {
    myFixture.copyDirectoryToProject(getTestName(true), "");
    myFixture.runWithSourceRoots(Lists.newArrayList(
      myFixture.findFileInTempDir("root1"),
      myFixture.findFileInTempDir("root2")),
                       () -> {
                         myFixture.configureByFile("a.py");
                         assertNull(myFixture.complete(CompletionType.BASIC, 2));
                         myFixture.checkResultByFile(getTestName(true) + "/a.after.py");
                       });
  }

  private void doTestImportFromLocalPython3NamespacePackage() {
    runWithLanguageLevel(LanguageLevel.PYTHON35, () -> {
      myFixture.copyDirectoryToProject(getTestName(true), "");
      myFixture.runWithSourceRoots(Lists.newArrayList(myFixture.findFileInTempDir("root1"), myFixture.findFileInTempDir("root2")), () -> {
        myFixture.configureByFile("root1/pkg/test.py");
        List<String> lookupStrings = ContainerUtil.map(myFixture.completeBasic(), LookupElement::getLookupString);
        assertContainsElements(lookupStrings, "foo", "bar", "baz");
      });
    });
  }

  // PY-62208
  public void testImportableNamesNotSuggestedImmediatelyInsideClassBody() {
    doMultiFileTest();
  }

  // PY-62208
  public void testImportableNamesSuggestedInsideOtherStatementsInsideClassBody() {
    doMultiFileTest();
  }

  // PY-62208
  public void testImportableNamesNotSuggestedImmediatelyInsideMatchStatement() {
    runWithLanguageLevel(LanguageLevel.getLatest(), () -> {
      doMultiFileTest();
    });
  }

  // PY-62208
  public void testImportableFunctionsAndVariablesNotSuggestedInsideTypeHints() {
    runWithLanguageLevel(LanguageLevel.getLatest(), () -> {
      doMultiFileTest();
    });
  }

  // PY-62208
  public void testImportableFunctionsAndVariablesNotSuggestedInsidePatterns() {
    runWithLanguageLevel(LanguageLevel.getLatest(), () -> {
      myFixture.copyDirectoryToProject(getTestName(true), "");
      myFixture.configureByFile("a.py");
      myFixture.complete(CompletionType.BASIC, 1);
      List<String> variants = myFixture.getLookupElementStrings();
      // TODO Use regular doMultiFileTest once PY-73173 is fixed
      assertDoesntContain(variants, "unique_var", "unique_func");
      assertContainsElements(variants, "unique_class");
    });
  }

  // PY-62208
  public void testNotReExportedNamesFromPrivateModulesNotSuggested() {
    doMultiFileTest();
  }

  // PY-62208
  public void testReExportedNamesFromPrivateModulesAreSuggested() {
    doMultiFileTest();
  }

  // PY-62208
  public void testAlreadyImportedNamesNotSuggestedTwice() {
    doMultiFileTest();
  }

  // PY-83412
  public void testImportableNamesNotSuggestedInTheMiddleOfAssignmentTargets() {
    myFixture.copyDirectoryToProject(getTestName(true), "");
    myFixture.configureByFile("a.py");
    myFixture.complete(CompletionType.BASIC, 1);
    assertDoesntContain(myFixture.getLookupElementStrings(), "foo_func");
  }

  // PY-62208
  public void testAlreadyImportedNamesNotSuggestedTwiceInsidePatterns() {
    runWithLanguageLevel(LanguageLevel.getLatest(), () -> {
      myFixture.copyDirectoryToProject(getTestName(true), "");
      myFixture.configureByFile("a.py");
      myFixture.complete(CompletionType.BASIC, 1);
      List<String> variants = myFixture.getLookupElementStrings();
      // TODO Use regular doMultiFileTest once PY-73173 is fixed
      assertEquals(1, Collections.frequency(variants, "MyClass"));
    });
  }

  // PY-62208
  public void testTooShortImportableNamesSuggestedOnlyInExtendedCompletion() {
    myFixture.copyDirectoryToProject(getTestName(true), "");
    myFixture.configureByFile("a.py");
    myFixture.complete(CompletionType.BASIC, 1);
    List<String> basicCompletionVariants = myFixture.getLookupElementStrings();
    assertDoesntContain(basicCompletionVariants, "c1", "c2");
    myFixture.complete(CompletionType.BASIC, 2);
    List<String> extendedCompletionVariants = myFixture.getLookupElementStrings();
    assertContainsElements(extendedCompletionVariants, "c1", "c2");
  }

  private static void runWithImportableNamesInBasicCompletionDisabled(@NotNull Runnable action) {
    PyCodeInsightSettings settings = PyCodeInsightSettings.getInstance();
    boolean old = settings.INCLUDE_IMPORTABLE_NAMES_IN_BASIC_COMPLETION;
    settings.INCLUDE_IMPORTABLE_NAMES_IN_BASIC_COMPLETION = false;
    try {
      action.run();
    }
    finally {
      settings.INCLUDE_IMPORTABLE_NAMES_IN_BASIC_COMPLETION = old;
    }
  }

  @Override
  protected @NotNull String getTestDataPath() {
    return super.getTestDataPath() + "/completion";
  }
}
