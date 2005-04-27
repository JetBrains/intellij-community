package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.refactoring.actions.TypeCookAction;

public class GenerifyFileFix implements IntentionAction {
  private final PsiFile myFile;

  public GenerifyFileFix(PsiFile file) {
    myFile = file;
  }

  public String getText() {
    return "Try to generify '"+myFile.getName()+"'";
  }

  public String getFamilyName() {
    return "Generify File";
  }

  public boolean isAvailable(Project project, Editor editor, PsiFile file) {
    return myFile.isValid() && PsiManager.getInstance(project).isInProject(myFile);
  }

  public void invoke(Project project, Editor editor, PsiFile file) {
    if (!CodeInsightUtil.prepareFileForWrite(myFile)) return;
    new TypeCookAction().getHandler().invoke(project, editor, file, null);
  }

  public boolean startInWriteAction() {
    return false;
  }
}
