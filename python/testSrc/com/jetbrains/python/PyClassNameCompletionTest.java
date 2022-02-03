// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python;

import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.project.DumbService;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.testFramework.fixtures.TestLookupElementPresentation;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.codeInsight.PyCodeInsightSettings;
import com.jetbrains.python.codeInsight.userSkeletons.PyUserSkeletonsUtil;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.formatter.PyCodeStyleSettings;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyQualifiedNameOwner;
import com.jetbrains.python.psi.resolve.QualifiedNameFinder;
import com.jetbrains.python.psi.stubs.PyClassNameIndex;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Objects;


public class PyClassNameCompletionTest extends PyTestCase {

  public void testSimple() {
    doTest();
  }

  public void testReuseExisting() {
    doTest();
  }

  public void testQualified() {
    doTestWithoutFromImport();
  }

  public void testFunction() {
    doTest();
  }

  public void testModule() {
    runWithAdditionalFileInLibDir("collections.py", "", (__) -> doTest());
  }

  public void testVariable() {
    runWithAdditionalFileInLibDir("datetime.py", "MAXYEAR = 10", (__) -> doTest());
  }

  public void testSubmodule() {  // PY-7887
    doTest();
  }

  public void testSubmoduleRegularImport() {  // PY-7887
    doTestWithoutFromImport();
  }

  public void testStringLiteral() { // PY-10526
    doTest();
  }
  private void doTestWithoutFromImport() {
    final PyCodeInsightSettings settings = PyCodeInsightSettings.getInstance();
    boolean oldValue = settings.PREFER_FROM_IMPORT;
    settings.PREFER_FROM_IMPORT = false;
    try {
      doTest();
    }
    finally {
      settings.PREFER_FROM_IMPORT = oldValue;
    }
  }

  // PY-18688
  public void testTypeComment() {
    doTest();
  }

  // PY-22422
  public void testReformatUpdatedFromImport() {
    getPythonCodeStyleSettings().FROM_IMPORT_WRAPPING = CommonCodeStyleSettings.WRAP_ALWAYS;
    getPythonCodeStyleSettings().FROM_IMPORT_NEW_LINE_BEFORE_RIGHT_PARENTHESIS = true;
    getPythonCodeStyleSettings().FROM_IMPORT_NEW_LINE_AFTER_LEFT_PARENTHESIS = true;
    getPythonCodeStyleSettings().FROM_IMPORT_PARENTHESES_FORCE_IF_MULTILINE = true;
    getPythonCodeStyleSettings().FROM_IMPORT_TRAILING_COMMA_IF_MULTILINE = true;
    doTest();
  }

  // PY-3563
  public void testAlreadyImportedModulesPreference() {
    doTest();
  }

  // PY-25484
  public void testClassReexportedThroughDunderAll() {
    doTest();
  }

  // PY-23475
  public void testImportAddedAfterModuleLevelDunder() {
    doTest();
  }

  // PY-20976
  public void testOrderingLexicographicalBaseline() {
    doTestCompletionOrder("a.foo", "b.foo");
  }

  // PY-20976
  public void testOrderingLocalBeforeStdlib() {
    runWithAdditionalFileInLibDir(
      "sys.py",
      "path = 10",
      (__) -> doTestCompletionOrder("local_pkg.path", "local_pkg.local_module.path", "sys.path")
    );
  }

  // PY-20976
  public void testOrderingUnderscoreInPath() {
    doTestCompletionOrder("b.foo", "_a.foo");
  }

  // PY-20976
  public void testOrderingSymbolBeforeModule() {
    doTestCompletionOrder("b.foo", "a.foo");
  }

  // PY-20976
  public void testOrderingModuleBeforePackage() {
    doTestCompletionOrder("b.foo", "a.foo");
  }

  // PY-20976
  public void testOrderingFileHasImportFromSameFile() {
    doTestCompletionOrder("b.foo", "a.foo");
  }

  // PY-20976
  public void testOrderingPathComponentsNumber() {
    doTestCompletionOrder("c.foo", "b.c.foo", "a.b.c.foo");
  }

  // PY-20976
  public void testCombinedOrdering() {
    runWithAdditionalFileInLibDir(
      "sys.py",
      "path = 10",
      (__) -> doTestCompletionOrder("combinedOrdering.path", "first.foo.path", "sys.path", "_second.bar.path")
    );
  }

  // PY-20976
  public void testOrderingUnderscoreInName() {
    doTestCompletionOrder("c.foo", "b._foo", "a.__foo__");
  }

  // PY-44586
  public void testNoDuplicatesForStubsAndOverloads() {
    doExtendedCompletion();
    List<String> allVariants = myFixture.getLookupElementStrings();
    assertNotNull(allVariants);
    assertEquals(1, Collections.frequency(allVariants, "my_func"));
  }

  // PY-45541
  public void testCanonicalImportPathUsedAsLookupTailText() {
    LookupElement[] lookupElements = doExtendedCompletion();
    LookupElement reexportedFunc = ContainerUtil.find(lookupElements, variant -> variant.getLookupString().equals("my_func"));
    assertNotNull(reexportedFunc);
    TestLookupElementPresentation funcPresentation = TestLookupElementPresentation.renderReal(reexportedFunc);
    assertEquals(" (pkg)", funcPresentation.getTailText());

    LookupElement notExportedVar = ContainerUtil.find(lookupElements, variant -> variant.getLookupString().equals("my_var"));
    assertNotNull(notExportedVar);
    TestLookupElementPresentation varPresentation = TestLookupElementPresentation.renderReal(notExportedVar);
    assertEquals(" (pkg.mod)", varPresentation.getTailText());
  }

  // PY-45566
  public void testPythonSkeletonsVariantsNotSuggested() {
    LookupElement[] lookupElements = doExtendedCompletion();

    LookupElement ndarray = ContainerUtil.find(lookupElements, variant -> variant.getLookupString().equals("ndarray"));
    assertNull(ndarray);

    PyClass ndarrayUserSkeleton = PyClassNameIndex.findClass("numpy.core.multiarray.ndarray", myFixture.getProject());
    if (ndarrayUserSkeleton == null) {
      System.out.println("Dumb mode: " + DumbService.isDumb(myFixture.getProject()));
      dumpSdkRoots();
    }
    assertNotNull(ndarrayUserSkeleton);
    assertTrue(PyUserSkeletonsUtil.isUnderUserSkeletonsDirectory(ndarrayUserSkeleton.getContainingFile()));
  }

  // PY-46381
  public void testThirdPartyPackageTestsNotSuggested() {
    runWithAdditionalClassEntryInSdkRoots(getTestName(true) + "/site-packages", () -> {
      myFixture.copyDirectoryToProject(getTestName(true) + "/src", "");
      myFixture.configureByFile("main.py");
      LookupElement[] variants = myFixture.complete(CompletionType.BASIC, 2);
      assertNotNull(variants);
      List<String> variantQNames = ContainerUtil.mapNotNull(variants, PyClassNameCompletionTest::getElementQualifiedName);
      assertDoesntContain(variantQNames, "mypkg.test.test_mod.test_func");
      assertContainsElements(variantQNames, "mod.func", "tests.test_func", "mypkg.mod.func");
    });
  }

  // PY-46381
  public void testThirdPartyPackageBundledDependenciesNotSuggested() {
    runWithAdditionalClassEntryInSdkRoots(getTestName(true) + "/site-packages", () -> {
      myFixture.copyDirectoryToProject(getTestName(true) + "/src", "");
      myFixture.configureByFile("main.py");
      LookupElement[] variants = myFixture.complete(CompletionType.BASIC, 2);
      assertNotNull(variants);
      List<String> variantQNames = ContainerUtil.mapNotNull(variants, PyClassNameCompletionTest::getElementQualifiedName);
      assertDoesntContain(variantQNames, "pip._vendor.requests", "pip._vendor.requests.request");
      assertContainsElements(variantQNames, "requests", "requests.request");
    });
  }

  @Nullable
  private static String getElementQualifiedName(@NotNull LookupElement le) {
    PsiElement element = le.getPsiElement();
    if (element instanceof PyQualifiedNameOwner) {
      return ((PyQualifiedNameOwner)element).getQualifiedName();
    }
    else if (element instanceof PsiFileSystemItem) {
      return Objects.toString(QualifiedNameFinder.findShortestImportableQName((PsiFileSystemItem)element));
    }
    return null;
  }

  private void doTest() {
    LookupElement[] lookupElements = doExtendedCompletion();
    if (lookupElements != null) {
      myFixture.finishLookup(Lookup.NORMAL_SELECT_CHAR);
    }
    myFixture.checkResultByFile(getTestName(true) + "/" + getTestName(true) + ".after.py", true);
  }

  private void doTestCompletionOrder(String @NotNull ... expected) {
    LookupElement[] lookupElements = doExtendedCompletion();
    List<String> qNames = StreamEx.of(lookupElements)
      .map(LookupElement::getPsiElement)
      .nonNull()
      .map(PyClassNameCompletionTest::extractQualifiedName)
      .toList();
    assertContainsInRelativeOrder(qNames, expected);
  }

  private LookupElement @Nullable [] doExtendedCompletion() {
    myFixture.copyDirectoryToProject(getTestName(true), "");
    myFixture.configureFromTempProjectFile(getTestName(true) + ".py");
    return myFixture.complete(CompletionType.BASIC, 2);
  }

  @Nullable
  private static String extractQualifiedName(@NotNull PsiElement element) {
    if (element instanceof PyQualifiedNameOwner) {
      return ((PyQualifiedNameOwner)element).getQualifiedName();
    }
    else {
      final PsiFileSystemItem item;
      if (element instanceof PsiDirectory) item = (PsiDirectory)element;
      else item = element.getContainingFile();

      return Objects.toString(QualifiedNameFinder.findShortestImportableQName(item));
    }
  }

  @NotNull
  private PyCodeStyleSettings getPythonCodeStyleSettings() {
    return getCodeStyleSettings().getCustomSettings(PyCodeStyleSettings.class);
  }

  @Override
  protected String getTestDataPath() {
    return super.getTestDataPath() + "/completion/className/";
  }
}
