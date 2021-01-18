// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python;

import com.intellij.codeInsight.documentation.DocumentationManager;
import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction;
import com.intellij.lang.documentation.DocumentationProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.ResolveResult;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.ThrowableRunnable;
import com.jetbrains.python.documentation.PythonDocumentationProvider;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.inspections.unresolvedReference.PyUnresolvedReferencesInspection;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PyOverloadsProcessingPerformanceTest extends PyTestCase {
  public void testComputingResultTypeWithCodeAnalysisContext() {
    PyCallExpression call = prepareTestDataAndGetCallExpr();
    TypeEvalContext context = TypeEvalContext.codeAnalysis(myFixture.getProject(), myFixture.getFile());
    assertType("int", call, context);
  }

  public void testComputingResultTypeWithUserInitiatedContext() {
    PyCallExpression call = prepareTestDataAndGetCallExpr();
    TypeEvalContext context = TypeEvalContext.userInitiated(myFixture.getProject(), myFixture.getFile());
    assertType("int", call, context);
  }

  public void testNavigatingToDefinition() {
    PyCallExpression call = prepareTestDataAndGetCallExpr();
    PsiElement element = GotoDeclarationAction.findTargetElement(myFixture.getProject(), myFixture.getEditor(), call.getTextOffset());
    assertInstanceOf(element, PyFunction.class);
  }

  public void testQuickDocumentationRendering() {
    PyCallExpression call = prepareTestDataAndGetCallExpr();
    DocumentationProvider docProvider = new PythonDocumentationProvider();
    PsiElement leaf = PsiTreeUtil.getDeepestFirst(call);
    DocumentationManager docManager = DocumentationManager.getInstance(myFixture.getProject());
    PsiElement docTarget = docManager.findTargetElement(myFixture.getEditor(), leaf.getTextOffset(), myFixture.getFile(), leaf);
    doPerformanceTestResettingCaches("Rendering Quick Documentation", 100, () -> {
      String doc = docProvider.generateDoc(docTarget, leaf);
      // Smoke test that it's the right documentation.
      assertTrue(doc.contains("def <b>func</b>(x"));
    });
  }

  public void testUnresolvedReferencesInspectionPass() {
    myFixture.copyDirectoryToProject("", "");
    myFixture.enableInspections(PyUnresolvedReferencesInspection.class);
    doPerformanceTestResettingCaches("Pass of Unresolved References inspection", 100, () -> {
      myFixture.configureByFile("main.py");
      myFixture.checkHighlighting();
    });
  }

  public void testOverloadsNotDuplicatedInMultiResolveResults() {
    PyCallExpression call = prepareTestDataAndGetCallExpr();
    PyReferenceExpression refExpr = assertInstanceOf(call.getCallee(), PyReferenceExpression.class);
    ResolveResult[] resolveResults = refExpr.getReference().multiResolve(false);
    assertTrue("Too many resolve results " + resolveResults.length,
               resolveResults.length == 1000 || resolveResults.length == 1001);
  }

  private void doPerformanceTestResettingCaches(@NotNull String text, int expectedMs, ThrowableRunnable<Throwable> runnable) {
    PsiManager psiManager = myFixture.getPsiManager();
    PlatformTestUtil.startPerformanceTest(text, expectedMs, runnable)
      .setup(() -> {
        psiManager.dropPsiCaches();
        psiManager.dropResolveCaches();
      })
      .assertTiming();
  }

  @NotNull
  private PyCallExpression prepareTestDataAndGetCallExpr() {
    myFixture.copyDirectoryToProject("", "");
    myFixture.configureByFile("main.py");
    PyCallExpression call = myFixture.findElementByText("func(999, 'foo')", PyCallExpression.class);
    assertNotNull(call);
    return call;
  }

  @Override
  protected String getTestDataPath() {
    return super.getTestDataPath() + "/performance/overloads/";
  }

  @Override
  protected @Nullable LightProjectDescriptor getProjectDescriptor() {
    return ourPy3Descriptor;
  }
}
