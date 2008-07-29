package com.intellij.testIntegration;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.testIntegration.createTest.CreateTestAction;
import com.intellij.util.IncorrectOperationException;

public class JavaTestCreator implements TestCreator {
  public void createTest(Project project, Editor editor, PsiFile file) {
    try {
      CreateTestAction action = new CreateTestAction();
      if (action.isAvailable(project, editor, file)) {
        action.invoke(project, editor, file);
      }
    }
    catch (IncorrectOperationException e) {
      throw new RuntimeException(e);
    }
  }
}
