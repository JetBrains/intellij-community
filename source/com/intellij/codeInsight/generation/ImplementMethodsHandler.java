package com.intellij.codeInsight.generation;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;

public class ImplementMethodsHandler implements CodeInsightActionHandler{
  public final void invoke(final Project project, final Editor editor, PsiFile file) {
    Document document = editor.getDocument();
    if (!file.isWritable()){
      document.fireReadOnlyModificationAttempt();
      return;
    }
    PsiClass aClass = OverrideImplementUtil.getContextClass(project, editor, file);
    if (aClass != null) {
      OverrideImplementUtil.chooseAndImplementMethods(project, editor, aClass);
    }
  }

  public boolean startInWriteAction() {
    return false;
  }
}