package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightMessageUtil;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.refactoring.safeDelete.SafeDeleteHandler;
import com.intellij.util.IncorrectOperationException;

import java.text.MessageFormat;

public class SafeDeleteFix implements IntentionAction {
  private final PsiElement myElement;

  public SafeDeleteFix(PsiElement element) {
    myElement = element;
  }

  public String getText() {
    final String text = MessageFormat.format("Safe delete ''{0}''",
        new Object[]{
          HighlightMessageUtil.getSymbolName(myElement, PsiSubstitutor.EMPTY),
        });
    return text;
  }

  public String getFamilyName() {
    return "Safe delete";
  }

  public boolean isAvailable(Project project, Editor editor, PsiFile file) {
    PsiElement element = myElement.getContainingFile();
    return myElement != null && myElement.isValid() && element.getManager().isInProject(element);
  }

  public void invoke(Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    if (!CodeInsightUtil.prepareFileForWrite(myElement.getContainingFile())) return;
    new SafeDeleteHandler().invoke(project, new PsiElement[]{myElement}, false);
  }

  public boolean startInWriteAction() {
    return false;
  }

}
