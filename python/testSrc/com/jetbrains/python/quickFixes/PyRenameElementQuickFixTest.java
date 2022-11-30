// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.quickFixes;

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.lang.injection.MultiHostInjector;
import com.intellij.lang.injection.MultiHostRegistrar;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.refactoring.rename.NameSuggestionProvider;
import com.intellij.refactoring.rename.PreferrableNameSuggestionProvider;
import com.intellij.testFramework.ServiceContainerUtil;
import com.intellij.testFramework.TestDataPath;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyQuickFixTestCase;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.inspections.PyCompatibilityInspection;
import com.jetbrains.python.inspections.PyPep8NamingInspection;
import com.jetbrains.python.inspections.PyProtectedMemberInspection;
import com.jetbrains.python.inspections.PyShadowingBuiltinsInspection;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

@TestDataPath("$CONTENT_ROOT/../testData//quickFixes/RenameElementQuickFixTest/")
public class PyRenameElementQuickFixTest extends PyQuickFixTestCase {

  // The value renamed element will have after quick fix rename
  public static final String RENAME_RESULT_BY_RENAME_HANDLER = "A_NEW_NAME";

  @Override
  public void setUp() throws Exception {
    super.setUp();
    registerTestNameSuggestionProvider(getTestRootDisposable());
  }

  /**
   * Registers {@link NameSuggestionProvider} to provide the name that will replace the one under the caret during the test.
   *
   * @see PyRenameElementQuickFixTest.RENAME_RESULT_BY_RENAME_HANDLER
   */
  public static void registerTestNameSuggestionProvider(Disposable disposable) {
    ServiceContainerUtil.registerExtension(ApplicationManager.getApplication(), NameSuggestionProvider.EP_NAME, createTestNameSuggestionProvider(), disposable);
  }

  /**
   * Create {@link NameSuggestionProvider} that will provide {@link RENAME_RESULT_BY_RENAME_HANDLER} to the list of variants.
   *
   * Note, that according to the renaming logic the {@link RENAME_RESULT_BY_RENAME_HANDLER} should lexicographically precede all other name suggestions to
   * appear in testing result.
   *
   * @see com.intellij.refactoring.rename.PsiElementRenameHandler#rename(PsiElement, Project, PsiElement, Editor, String
   */
  @NotNull
  private static NameSuggestionProvider createTestNameSuggestionProvider() {
    return new PreferrableNameSuggestionProvider() {
      @NotNull
      @Override
      public SuggestedNameInfo getSuggestedNames(PsiElement element, @Nullable PsiElement nameSuggestionContext, Set<String> result) {
        result.add(RENAME_RESULT_BY_RENAME_HANDLER);
        return new SuggestedNameInfo(new String[]{RENAME_RESULT_BY_RENAME_HANDLER}) {};
      }
    };
  }

  public void testProtectedMember() {
    doQuickFixTest(PyProtectedMemberInspection.class, PyBundle.message("QFIX.NAME.rename.element"));
  }

  /**
   * Note that the renaming is performed by {@link com.intellij.refactoring.rename.inplace.VariableInplaceRenamer}
   * and therefore the result is different from {@link RENAME_RESULT_BY_RENAME_HANDLER}.
   */
  public void testPep8() {
    doQuickFixTest(PyPep8NamingInspection.class, PyBundle.message("QFIX.NAME.rename.element"));
  }

  public void testPep8Class() {
    doQuickFixTest(PyPep8NamingInspection.class, PyBundle.message("QFIX.NAME.rename.element"));
  }

  public void testPep8Function() {
    doQuickFixTest(PyPep8NamingInspection.class, PyBundle.message("QFIX.NAME.rename.element"));
  }

  public void testShadowingBuiltins() {
    doQuickFixTest(PyShadowingBuiltinsInspection.class, PyBundle.message("QFIX.NAME.rename.element"));
  }

  // PY-16098
  public void testRenameAsyncClassInPy36() {
    runWithLanguageLevel(LanguageLevel.PYTHON36,
                         () -> doQuickFixTest(PyCompatibilityInspection.class, PyBundle.message("QFIX.NAME.rename.element")));
  }

  // PY-16098
  public void testRenameAwaitClassInPy36() {
    runWithLanguageLevel(LanguageLevel.PYTHON36,
                         () -> doQuickFixTest(PyCompatibilityInspection.class, PyBundle.message("QFIX.NAME.rename.element")));
  }

  // PY-16098
  public void testRenameAsyncFunctionInPy36() {
    runWithLanguageLevel(LanguageLevel.PYTHON36,
                         () -> doQuickFixTest(PyCompatibilityInspection.class, PyBundle.message("QFIX.NAME.rename.element")));
  }

  // PY-16098
  public void testRenameAwaitFunctionInPy36() {
    runWithLanguageLevel(LanguageLevel.PYTHON36,
                         () -> doQuickFixTest(PyCompatibilityInspection.class, PyBundle.message("QFIX.NAME.rename.element")));
  }

  // PY-16098
  public void testRenameAsyncVariableInPy36() {
    runWithLanguageLevel(LanguageLevel.PYTHON36,
                         () -> doQuickFixTest(PyCompatibilityInspection.class, PyBundle.message("QFIX.NAME.rename.element")));
  }

  // PY-16098
  public void testRenameAwaitVariableInPy36() {
    runWithLanguageLevel(LanguageLevel.PYTHON36,
                         () -> doQuickFixTest(PyCompatibilityInspection.class, PyBundle.message("QFIX.NAME.rename.element")));
  }

  // PY-30827
  public void testRenameInInjectedFragment() {
    testInInjectedLanguageFragment(
      () -> doQuickFixTest(PyPep8NamingInspection.class, PyBundle.message("QFIX.NAME.rename.element")));
  }

  /**
   * Test with language injection.
   *
   * Python is injected into string literal before running test code.
   */
  private void testInInjectedLanguageFragment(@NotNull Runnable runnable) {
    Disposable testDisposable = Disposer.newDisposable();
    injectPythonLanguage(testDisposable);
    runnable.run();
    Disposer.dispose(testDisposable);
  }

  /**
   * @param testDisposable {@link Disposable} to trigger language injector disposal
   */
  private void injectPythonLanguage(Disposable testDisposable) {
    final InjectedLanguageManager manager = InjectedLanguageManager.getInstance(myFixture.getProject());

    MultiHostInjector multiHostInjector = new MultiHostInjector() {
      @Override
      public void getLanguagesToInject(@NotNull MultiHostRegistrar registrar, @NotNull PsiElement context) {
        TextRange range = new TextRange(4, context.getTextLength() - 4);
        registrar.startInjecting(PythonLanguage.INSTANCE)
                 .addPlace(null, null, (PsiLanguageInjectionHost)context, range)
                 .doneInjecting();

      }
      @NotNull
      @Override
      public List<Class<? extends PsiElement>> elementsToInjectIn() {
        return List.of(PyStringLiteralExpression.class);
      }
    };
    manager.registerMultiHostInjector(multiHostInjector, testDisposable);
  }
}
