package com.intellij.codeInspection.ex;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;

/**
 * @author max
 */
public class QuickFixWrapper implements IntentionAction {
  private ProblemDescriptor myDescriptor;

  public QuickFixWrapper(ProblemDescriptor descriptor) {
    myDescriptor = descriptor;
  }

  public String getText() {
    return getFamilyName();
  }

  public String getFamilyName() {
    return myDescriptor.getFix().getName();
  }

  public boolean isAvailable(Project project, Editor editor, PsiFile file) {
    PsiElement psiElement = myDescriptor.getPsiElement();
    return psiElement != null && psiElement.isValid();
  }

  public void invoke(Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    if (!CodeInsightUtil.prepareFileForWrite(file)) return;
    myDescriptor.getFix().applyFix(project, myDescriptor);
  }

  public boolean startInWriteAction() {
    return true;
  }
}
