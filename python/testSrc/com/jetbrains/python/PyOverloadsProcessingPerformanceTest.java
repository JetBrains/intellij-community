// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python;

import com.intellij.codeInsight.documentation.DocumentationManager;
import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction;
import com.intellij.lang.documentation.DocumentationProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.ResolveResult;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.SkipSlowTestLocally;
import com.intellij.util.ThrowableRunnable;
import com.jetbrains.python.documentation.PythonDocumentationProvider;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.inspections.PyArgumentListInspection;
import com.jetbrains.python.inspections.unresolvedReference.PyUnresolvedReferencesInspection;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.types.TypeEvalContext;
import com.jetbrains.python.pyi.PyiUtil;
import com.jetbrains.python.sdk.PythonSdkUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@SkipSlowTestLocally
public class PyOverloadsProcessingPerformanceTest extends PyTestCase {
  private static final int NUMBER_OF_OVERLOADS = 1000;

  private VirtualFile myRoot;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    myRoot = StandardFileSystems.local().refreshAndFindFileByPath(getTestDataPath() + "/fixture");
    final Sdk sdk = PythonSdkUtil.findPythonSdk(myFixture.getModule());
    final SdkModificator modificator = sdk.getSdkModificator();
    assertNotNull(modificator);
    modificator.addRoot(myRoot, OrderRootType.CLASSES);
    modificator.commitChanges();
  }

  @Override
  public void tearDown() throws Exception {
    try {
      final Sdk sdk = PythonSdkUtil.findPythonSdk(myFixture.getModule());
      final SdkModificator modificator = sdk.getSdkModificator();
      assertNotNull(modificator);
      modificator.removeRoot(myRoot, OrderRootType.CLASSES);
      modificator.commitChanges();
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  public void testComputingResultTypeWithCodeAnalysisContext() {
    PyCallExpression call = configureAndGetCallExprUnderCaret("mainQualified.py");
    doPerformanceTestResettingCaches("Computing result type with code analysis context", 1200, () -> {
      TypeEvalContext context = TypeEvalContext.codeAnalysis(myFixture.getProject(), myFixture.getFile());
      assertType("int", call, context);
    });
  }

  public void testComputingResultTypeWithUserInitiatedContext() {
    PyCallExpression call = configureAndGetCallExprUnderCaret("mainQualified.py");
    doPerformanceTestResettingCaches("Computing result type with user initiated context", 4000, () -> {
      TypeEvalContext context = TypeEvalContext.userInitiated(myFixture.getProject(), myFixture.getFile());
      assertType("int", call, context);
    });
  }

  public void testNavigatingToDefinition() {
    configureAndGetCallExprUnderCaret("mainQualified.py");
    Project project = myFixture.getProject();
    doPerformanceTestResettingCaches("Navigating to definition", 600, () -> {
      TypeEvalContext context = TypeEvalContext.userInitiated(project, myFixture.getFile());
      PsiElement target = GotoDeclarationAction.findTargetElement(project, myFixture.getEditor(), myFixture.getCaretOffset());
      assertNotNull(target);
      PyFunction function = assertInstanceOf(target.getNavigationElement(), PyFunction.class);
      assertFalse(PyiUtil.isOverload(function, context));
    });
  }

  public void testQuickDocumentationRendering() {
    configureAndGetCallExprUnderCaret("mainQualified.py");
    DocumentationProvider docProvider = new PythonDocumentationProvider();
    PsiElement leaf = myFixture.getFile().findElementAt(myFixture.getCaretOffset());
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
    doPerformanceTestResettingCaches("Pass of Unresolved References inspection", 1200, () -> {
      myFixture.configureByFile("mainQualified.py");
      myFixture.checkHighlighting();
    });
  }

  public void testArgumentListInspectionPass() {
    myFixture.copyDirectoryToProject("", "");
    myFixture.enableInspections(PyArgumentListInspection.class);
    doPerformanceTestResettingCaches("Pass of Incorrect Call Arguments inspection", 1200, () -> {
      myFixture.configureByFile("mainQualified.py");
      myFixture.checkHighlighting();
    });
  }

  public void testOverloadsNotDuplicatedInReferenceMultiResolveResults() {
    PyCallExpression call = configureAndGetCallExprUnderCaret("mainUnqualified.py");
    PyReferenceExpression refExpr = assertInstanceOf(call.getCallee(), PyReferenceExpression.class);
    ResolveResult[] resolveResults = refExpr.getReference().multiResolve(false);
    // Overloads, lower-priority imported name and the function itself.
    assertEquals(NUMBER_OF_OVERLOADS + 2, resolveResults.length);
  }

  public void testOverloadsNotDuplicatedInQualifiedReferenceMultiResolveResults() {
    PyCallExpression call = configureAndGetCallExprUnderCaret("mainQualified.py");
    PyReferenceExpression expr = assertInstanceOf(call.getCallee(), PyReferenceExpression.class);
    ResolveResult[] resolveResults = expr.getReference().multiResolve(false);
    // Overloads only.
    assertEquals(NUMBER_OF_OVERLOADS, resolveResults.length);
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
  private PyCallExpression configureAndGetCallExprUnderCaret(@NotNull String fileName) {
    myFixture.configureByFile(fileName);
    PsiElement underCaret = myFixture.getFile().findElementAt(myFixture.getCaretOffset());
    assertNotNull(underCaret);
    PyCallExpression call = PsiTreeUtil.getParentOfType(underCaret, PyCallExpression.class);
    assertNotNull(call);
    return call;
  }

  @Override
  protected String getTestDataPath() {
    return super.getTestDataPath() + "/performance/overloads/";
  }

  @Override
  protected @Nullable LightProjectDescriptor getProjectDescriptor() {
    return ourPyLatestDescriptor;
  }
}
