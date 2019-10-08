// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python;

import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.jetbrains.python.codeInsight.PyCodeInsightSettings;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.formatter.PyCodeStyleSettings;
import com.jetbrains.python.psi.PyQualifiedNameOwner;
import com.jetbrains.python.psi.resolve.QualifiedNameFinder;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * @author yole
 */
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
      (__) -> doTestCompletionOrder("main.path", "first.foo.path", "sys.path", "_second.bar.path")
    );
  }

  // PY-20976
  public void testOrderingUnderscoreInName() {
    doTestCompletionOrder("c.foo", "b._foo", "a.__foo__");
  }

  private void doTest() {
    final String path = "/completion/className/" + getTestName(true);
    myFixture.copyDirectoryToProject(path, "");
    myFixture.configureFromTempProjectFile(getTestName(true) + ".py");
    myFixture.complete(CompletionType.BASIC, 2);
    if (myFixture.getLookupElements() != null) {
      myFixture.finishLookup(Lookup.NORMAL_SELECT_CHAR);
    }
    myFixture.checkResultByFile(path + "/" + getTestName(true) + ".after.py", true);
  }

  private void doTestCompletionOrder(@NotNull String... expected) {
    myFixture.copyDirectoryToProject("/completion/className/" + getTestName(true), "");
    myFixture.configureByFile("main.py");
    myFixture.complete(CompletionType.BASIC, 2);
    List<String> qNames = StreamEx.of(myFixture.getLookupElements())
      .map(LookupElement::getPsiElement)
      .nonNull()
      .map(PyClassNameCompletionTest::extractQualifiedName)
      .toList();
    assertContainsInRelativeOrder(qNames, expected);
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
}
