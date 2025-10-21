// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.jetbrains.python.documentation.PythonDocumentationProvider;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.inspections.PyTypeCheckerInspection;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.PyUtil;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;

public final class PyTypeCachingTest extends PyTestCase {
  public void testInspectionPassAfterUserInitiatedAction() {
    myFixture.copyDirectoryToProject("NonAnnotatedDefinitionInAnotherProjectFile", "");
    myFixture.configureFromTempProjectFile("a.py");

    // Here can be any action using TypeEvalContext with myAllowStubToAST==true
    PyTargetExpression argument = myFixture.findElementByText("x", PyTargetExpression.class);
    String argumentQuickDoc = new PythonDocumentationProvider().generateDoc(argument, argument);
    assertTrue(argumentQuickDoc.contains("<a href=\"psi_element://#typename#str\">str</a>"));

    myFixture.enableInspections(PyTypeCheckerInspection.class);
    myFixture.checkHighlighting();
  }

  public void testUserInitiatedActionAfterInspectionPass() {
    myFixture.copyDirectoryToProject("NonAnnotatedDefinitionInAnotherProjectFile", "");
    myFixture.configureFromTempProjectFile("a.py");

    myFixture.enableInspections(PyTypeCheckerInspection.class);
    myFixture.checkHighlighting();

    PyTargetExpression argument = myFixture.findElementByText("x", PyTargetExpression.class);
    String argumentQuickDoc = new PythonDocumentationProvider().generateDoc(argument, argument);
    assertTrue(argumentQuickDoc.contains("<a href=\"psi_element://#typename#str\">str</a>"));
  }

  public void testProjectPyiStubChangesLibraryType() {
    final String libRootPath = getTestDataPath() + "/" + getTestName(false) + "/lib";
    final VirtualFile libRoot = StandardFileSystems.local().findFileByPath(libRootPath);
    runWithAdditionalClassEntryInSdkRoots(libRoot, () -> {
      myFixture.copyDirectoryToProject(getTestName(false) + "/user", "");
      myFixture.configureFromTempProjectFile("main.py");
      assertType("int", 
                 myFixture.findElementByText("expr", PyTargetExpression.class), 
                 TypeEvalContext.userInitiated(myFixture.getProject(), myFixture.getFile()));

      // Change the return type annotation in the project .pyi stub
      PsiFile userPyiStub = myFixture.configureFromTempProjectFile("mylib2.pyi");
      PyReferenceExpression returnTypeHint = myFixture.findElementByText("int", PyReferenceExpression.class);
      WriteCommandAction.runWriteCommandAction(myFixture.getProject(), () -> {
        PyUtil.updateDocumentUnblockedAndCommitted(userPyiStub, document -> {
          document.replaceString(returnTypeHint.getTextOffset(), returnTypeHint.getTextRange().getEndOffset(), "str");
        });  
      });
      PyFunction func = myFixture.findElementByText("func", PyFunction.class);
      assertType("() -> str", func, TypeEvalContext.userInitiated(myFixture.getProject(), myFixture.getFile()));

      myFixture.configureFromTempProjectFile("main.py");
      assertType("str",
                 myFixture.findElementByText("expr", PyTargetExpression.class),
                 TypeEvalContext.userInitiated(myFixture.getProject(), myFixture.getFile()));
    });
  }

  @Override
  public @NotNull String getTestDataPath() {
    return super.getTestDataPath() + "/caching/";
  }
}
