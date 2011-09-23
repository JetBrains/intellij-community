package com.jetbrains.python.codeInsight.testIntegration;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.testIntegration.TestCreator;
import com.intellij.util.IncorrectOperationException;

/**
 * User: catherine
 */
public class PyTestCreator implements TestCreator {
  private static final Logger LOG = Logger.getInstance("#com.jetbrains.python.codeInsight.testIntegration.PyTestCreator");

  @Override
  public boolean isAvailable(Project project, Editor editor, PsiFile file) {
    CreateTestAction action = new CreateTestAction();
    PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());
    if (element != null)
      return action.isAvailable(project, editor, element);
    return false;
  }

  public void createTest(Project project, Editor editor, PsiFile file) {
    try {
      CreateTestAction action = new CreateTestAction();
      PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());
      if (action.isAvailable(project, editor, element)) {
        action.invoke(project, editor, file.getContainingFile());
      }
    }
    catch (IncorrectOperationException e) {
      LOG.warn(e);
    }
  }
}
