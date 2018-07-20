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
import com.jetbrains.python.inspections.unresolvedReference.PyUnresolvedReferencesInspection;
import com.jetbrains.python.psi.PyReferenceExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Mikhail Golubev
 */
public class PyAddImportQuickFixTest extends PyQuickFixTestCase {

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
    doMultiFileAutoImportTest("Import this name");
  }

  // PY-25234
  public void testBinarySkeletonStdlibModule() {
    doMultiFileAutoImportTest("Import 'sys'");
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
      assertOrderedEquals(candidates, "datetime from datetime", "mod.datetime");
      return false;
    });
  }

  // PY-28752
  public void testFullFromImportSourceNameInSuggestion() {
    doMultiFileAutoImportTest("Import 'ClassB from foo.bar.baz'");
  }

  private void doMultiFileAutoImportTest(@NotNull String hintPrefix) {
    doMultiFileAutoImportTest(hintPrefix, null);
  }

  private void doMultiFileAutoImportTest(@NotNull String hintPrefix, @Nullable Processor<AutoImportQuickFix> checkQuickfix) {
    myFixture.copyDirectoryToProject(getTestName(true), "");
    myFixture.enableInspections(PyUnresolvedReferencesInspection.class);
    myFixture.configureByFile("main.py");
    myFixture.checkHighlighting(true, false, false);

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
}
