package com.intellij.codeInsight;

import com.intellij.psi.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.util.IncorrectOperationException;

public interface CodeInsightActionHandler {
  void invoke(Project project, Editor editor, PsiFile file);

  boolean startInWriteAction();
}