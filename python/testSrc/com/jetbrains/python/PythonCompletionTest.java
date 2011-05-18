package com.jetbrains.python;

import com.intellij.codeInsight.lookup.LookupElement;
import com.jetbrains.python.documentation.DocStringFormat;
import com.jetbrains.python.documentation.PyDocumentationSettings;
import com.jetbrains.python.fixtures.PyLightFixtureTestCase;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.impl.PythonLanguageLevelPusher;

import java.util.Arrays;
import java.util.List;

public class PythonCompletionTest extends PyLightFixtureTestCase {

  private void doTest() {
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

  public void testKeywordAfterComment() {  // PY-697
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
    doTest();
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

  public void testPropertyType() {
    doTest();
  }

  public void testSeenMembers() {  // PY-1181
    final String testName = "completion/" + getTestName(true);
    myFixture.configureByFile(testName + ".py");
    final LookupElement[] elements = myFixture.completeBasic();
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

  public void testEmptyFile() {  // PY-1845
    myFixture.configureByText(PythonFileType.INSTANCE, "");
    myFixture.completeBasic();
    final List<String> elements = myFixture.getLookupElementStrings();
    assertTrue(elements.contains("import"));
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

  public void testNoParensForDecorator() {  // PY-2210
    doTest();
  }

  public void testNonlocal() {  // PY-2289
    doTest3K();
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

  public void testElseInCondExpr() {  // PY-2397
    doTest();
  }

  public void testLocalVarInDictKey() {  // PY-2558
    doTest();
  }

  public void testDictKeyPrefix() {
    doTest();
  }

  public void testFromDotImport() {  // PY-2772
    doTest3K();
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

  public void testLambdaInExpression() {  // PY-3150
    doTest();
  }

  public void testVeryPrivate() {  // PY-3246
    doTest();
  }

  public void testReexportModules() {  // PY-2385
    doMultiFileTest();
  }

  public void testEpydocParamTag() {
    doTest();
  }

  public void testEpydocTags() {
    final PyDocumentationSettings settings = PyDocumentationSettings.getInstance(myFixture.getProject());
    settings.setFormat(DocStringFormat.EPYTEXT);
    try {
      myFixture.configureByFile("completion/epydocTags.py");
      myFixture.completeBasic();
      assertTrue(myFixture.getLookupElementStrings().contains("@param"));
    }
    finally {
      settings.setFormat(DocStringFormat.PLAIN);
    }
  }

  public void testEpydocTagsMiddle() {
    final PyDocumentationSettings settings = PyDocumentationSettings.getInstance(myFixture.getProject());
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

  public void testNoneInArgList() {  // PY-3464
    doTest3K();
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
}
