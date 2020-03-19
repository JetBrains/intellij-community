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
package com.jetbrains.python.quickFixes;

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyQuickFixTestCase;
import com.jetbrains.python.codeInsight.imports.AutoImportQuickFix;
import com.jetbrains.python.codeInsight.imports.ImportCandidateHolder;
import com.jetbrains.python.codeInsight.imports.PythonImportUtils;
import com.jetbrains.python.formatter.PyCodeStyleSettings;
import com.jetbrains.python.inspections.unresolvedReference.PyUnresolvedReferencesInspection;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyReferenceExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Mikhail Golubev
 */
public class PyAddImportQuickFixTest extends PyQuickFixTestCase {
  @NotNull
  private PyCodeStyleSettings getPythonCodeStyleSettings() {
    return getCodeStyleSettings().getCustomSettings(PyCodeStyleSettings.class);
  }

  // PY-19773
  public void testReexportedName() {
    doMultiFileAutoImportTest("Import 'flask.request'");
  }

  public void testOsPathFunctions() {
    doMultiFileAutoImportTest("Import", fix -> {
      final List<ImportCandidateHolder> candidates = fix.getCandidates();
      final List<String> names = ContainerUtil.map(candidates, c -> c.getPresentableText("join"));
      assertSameElements(names, "os.path.join()");
      return true;
    });
  }

  // PY-19975
  public void testCanonicalNamesFromHigherLevelPackage() {
    doMultiFileAutoImportTest("Import", fix -> {
      final List<ImportCandidateHolder> candidates = fix.getCandidates();
      final List<String> names = ContainerUtil.map(candidates, c -> c.getPresentableText("MyClass"));
      assertOrderedEquals(names, "bar.MyClass", "foo.MyClass");
      return true;
    });
  }
  
  // PY-22422
  public void testAddParenthesesAndTrailingCommaToUpdatedFromImport() {
    getPythonCodeStyleSettings().FROM_IMPORT_WRAPPING = CommonCodeStyleSettings.WRAP_ALWAYS;
    getPythonCodeStyleSettings().FROM_IMPORT_NEW_LINE_BEFORE_RIGHT_PARENTHESIS = true;
    getPythonCodeStyleSettings().FROM_IMPORT_NEW_LINE_AFTER_LEFT_PARENTHESIS = true;
    getPythonCodeStyleSettings().FROM_IMPORT_PARENTHESES_FORCE_IF_MULTILINE = true;
    getPythonCodeStyleSettings().FROM_IMPORT_TRAILING_COMMA_IF_MULTILINE = true;
    doMultiFileAutoImportTest("Import 'bar from module'");
  }

  // PY-21563
  public void testCombineFromImportsForReferencesInTypeComment() {
    doMultiFileAutoImportTest("Import 'typing.Set'");
  }

  // PY-25234
  public void testBinarySkeletonStdlibModule() {
    runWithAdditionalFileInLibDir(
      "re.py",
      "",
      (__) ->
        runWithAdditionalFileInSkeletonDir(
          "sys.py",
          "# encoding: utf-8\n" +
          "# module sys\n" +
          "# from (built-in)\n" +
          "# by generator 1.138\n" +
          "path = 10",
          (___) -> doMultiFileAutoImportTest("Import 'sys'")
        )
    );
  }

  // PY-25234
  public void testUserSkeletonStdlibModule() {
    doMultiFileAutoImportTest("Import 'alembic'");
  }

  // PY-16176
  public void testAllVariantsSuggestedWhenExistingNonProjectImportFits() {
    doMultiFileAutoImportTest("Import", quickfix -> {
      final List<String> candidates = ContainerUtil.map(quickfix.getCandidates(), c -> c.getPresentableText("time"));
      assertOrderedEquals(candidates, "time from datetime", "time");
      return false;
    });
  }

  // PY-16176
  public void testExistingImportsAlwaysSuggestedFirstEvenIfLonger() {
    doMultiFileAutoImportTest("Import", quickfix -> {
      final List<String> candidates = ContainerUtil.map(quickfix.getCandidates(), c -> c.getPresentableText("ClassB"));
      assertOrderedEquals(candidates, "ClassB from long.pkg.path", "short.ClassB");
      return false;
    });
  }

  // PY-16176
  public void testExistingImportsAlwaysSuggestedFirstEvenIfNonProject() {
    doMultiFileAutoImportTest("Import", quickfix -> {
      final List<String> candidates = ContainerUtil.map(quickfix.getCandidates(), c -> c.getPresentableText("datetime"));
      assertOrderedEquals(candidates, "datetime(date) from datetime", "mod.datetime");
      return false;
    });
  }

  // PY-28752
  public void testFullFromImportSourceNameInSuggestion() {
    doMultiFileAutoImportTest("Import 'ClassB from foo.bar.baz'");
  }

  // PY-24450
  public void testAvailableForUnqualifiedDecoratorWithoutArguments() {
    doMultiFileAutoImportTest("Import 'pytest'");
  }

  // PY-24450
  public void testUnavailableForUnqualifiedDecoratorWithArguments() {
    doMultiFileNegativeTest("Import 'pytest'");
  }

  // PY-20100
  public void testAlwaysSplitFromImports() {
    getPythonCodeStyleSettings().OPTIMIZE_IMPORTS_ALWAYS_SPLIT_FROM_IMPORTS = true;
    doMultiFileAutoImportTest("Import 'mod.bar()'");
  }

  // PY-20976
  public void testCombinedElementOrdering() {
    runWithAdditionalFileInLibDir(
      "os/__init__.py",
      "",
      (__) ->
        runWithAdditionalFileInLibDir(
          "os/path.py",
          "",
          (___) -> doTestProposedImportsOrdering("path",
                                                 "path from sys", "first.path", "first.second.path()", "os.path", "first._third.path")
        )
    );
  }

  // PY-20976
  public void testOrderingLocalBeforeStdlib() {
    runWithAdditionalFileInLibDir(
      "sys.py",
      "path = 10",
      (__) -> doTestProposedImportsOrdering("path", "pkg.path", "sys.path")
    );
  }

  // PY-20976
  public void testOrderingUnderscoreInPath() {
    runWithAdditionalFileInLibDir(
      "sys.py",
      "path = 10",
      (__) -> doTestProposedImportsOrdering("path", "first.second.path", "sys.path", "_private.path")
    );
  }

  // PY-20976
  public void testOrderingSymbolBeforeModule() {
    doTestProposedImportsOrdering("foo", "first.module.foo()", "first.a.foo");
  }

  // PY-20976
  public void testOrderingModuleBeforePackage() {
    doTestProposedImportsOrdering("foo", "b.foo", "a.foo");
  }

  // PY-20976
  public void testOrderingPathComponentsNumber() {
    doTestProposedImportsOrdering("foo", "c.foo", "b.c.foo", "a.b.c.foo");
  }

  // PY-20976
  public void testOrderingWithExistingImport() {
    runWithAdditionalFileInLibDir(
      "os/__init__.py",
      "",
      (__) ->
        runWithAdditionalFileInLibDir(
          "os/path.py",
          "",
          (___) -> doTestProposedImportsOrdering("path", "path from sys", "src.path", "os.path")
        )
    );
  }

  // PY-34818
  public void testReferenceInsideFString() {
    runWithLanguageLevel(LanguageLevel.PYTHON36, () -> {
      doMultiFileAutoImportTest("Import");
    });
  }

  // PY-23968
  public void testOrderingOfNamesInFromImportBeginning() {
    getPythonCodeStyleSettings().OPTIMIZE_IMPORTS_SORT_IMPORTS = true;
    getPythonCodeStyleSettings().OPTIMIZE_IMPORTS_SORT_NAMES_IN_FROM_IMPORTS = true;
    doMultiFileAutoImportTest("Import");
  }

  // PY-23968
  public void testOrderingOfNamesInFromImportInTheMiddle() {
    getPythonCodeStyleSettings().OPTIMIZE_IMPORTS_SORT_IMPORTS = true;
    getPythonCodeStyleSettings().OPTIMIZE_IMPORTS_SORT_NAMES_IN_FROM_IMPORTS = true;
    doMultiFileAutoImportTest("Import");
  }

  // PY-23968
  public void testOrderingOfNamesInFromImportEnd() {
    getPythonCodeStyleSettings().OPTIMIZE_IMPORTS_SORT_IMPORTS = true;
    getPythonCodeStyleSettings().OPTIMIZE_IMPORTS_SORT_NAMES_IN_FROM_IMPORTS = true;
    doMultiFileAutoImportTest("Import");
  }

  private void doTestProposedImportsOrdering(@NotNull String text, String @NotNull ... expected) {
    doMultiFileAutoImportTest("Import", fix -> {
      final List<String> candidates = ContainerUtil.map(fix.getCandidates(), c -> c.getPresentableText(text));
      assertNotNull(candidates);
      assertContainsInRelativeOrder(candidates, expected);
      return false;
    });
  }

  private void doMultiFileAutoImportTest(@NotNull String hintPrefix) {
    doMultiFileAutoImportTest(hintPrefix, null);
  }

  private void doMultiFileAutoImportTest(@NotNull String hintPrefix, @Nullable Processor<AutoImportQuickFix> checkQuickfix) {
    configureMultiFileProject();

    final PsiElement hostUnderCaret = myFixture.getFile().findElementAt(myFixture.getCaretOffset());
    final PyReferenceExpression hostRefExpr = PsiTreeUtil.getParentOfType(hostUnderCaret, PyReferenceExpression.class);

    final InjectedLanguageManager injectionManager = InjectedLanguageManager.getInstance(myFixture.getProject());
    final PsiElement injectedUnderCaret = injectionManager.findInjectedElementAt(myFixture.getFile(), myFixture.getCaretOffset());
    final PyReferenceExpression injectionRefExpr = PsiTreeUtil.getParentOfType(injectedUnderCaret, PyReferenceExpression.class);

    final PyReferenceExpression unresolvedRefExpr = ObjectUtils.chooseNotNull(injectionRefExpr, hostRefExpr);
    assertNotNull(unresolvedRefExpr);
    final AutoImportQuickFix quickfix = PythonImportUtils.proposeImportFix(unresolvedRefExpr, unresolvedRefExpr.getReference());
    assertNotNull(quickfix);

    final boolean applyFix = checkQuickfix == null || checkQuickfix.process(quickfix);
    if (applyFix) {
      myFixture.launchAction(myFixture.findSingleIntention(hintPrefix));
      myFixture.checkResultByFile(getTestName(true) + "/main_after.py", true);
    }
  }

  private void doMultiFileNegativeTest(@NotNull String hintPrefix) {
    configureMultiFileProject();
    assertEmpty(myFixture.filterAvailableIntentions(hintPrefix));
  }

  private void configureMultiFileProject() {
    myFixture.copyDirectoryToProject(getTestName(true), "");
    myFixture.enableInspections(PyUnresolvedReferencesInspection.class);
    myFixture.configureByFile("main.py");
    myFixture.checkHighlighting(true, false, false);
  }
}
