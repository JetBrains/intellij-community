package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiNewExpression;
import com.intellij.util.IncorrectOperationException;

/**
 * Created by IntelliJ IDEA.
 * User: cdr
 * Date: Nov 29, 2002
 * Time: 3:02:17 PM
 * To change this template use Options | File Templates.
 */
public class RemoveNewQualifierFix implements IntentionAction {
  private final PsiNewExpression expression;
  private final PsiClass aClass;

  public RemoveNewQualifierFix(PsiNewExpression expression, PsiClass aClass) {
    this.expression = expression;
    this.aClass = aClass;
  }

  public String getText() {
    return "Remove qualifier";
  }

  public String getFamilyName() {
    return "Remove qualifier";
  }

  public boolean isAvailable(Project project, Editor editor, PsiFile file) {
    return
        expression != null
        && expression.isValid()
        && expression.getClassReference() != null
        && aClass != null
        && aClass.isValid()
        && expression.getManager().isInProject(expression);
  }

  public void invoke(Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    if (!CodeInsightUtil.prepareFileForWrite(expression.getContainingFile())) return;
    final PsiJavaCodeReferenceElement classReference = expression.getClassReference();
    expression.getQualifier().delete();
    classReference.bindToElement(aClass);
  }

  public boolean startInWriteAction() {
    return true;
  }

}
