package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightMessageUtil;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightUtil;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;

import java.text.MessageFormat;

public class AccessStaticViaInstanceFix implements IntentionAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.quickfix.AccessStaticViaInstanceFix");

  private final PsiReferenceExpression myExpression;
  private final PsiMember myMember;
  private final JavaResolveResult myResult;

  public AccessStaticViaInstanceFix(PsiReferenceExpression expression, JavaResolveResult result) {
    myExpression = expression;
    myMember = (PsiMember)result.getElement();
    myResult = result;
  }

  public String getText() {
    PsiClass aClass = myMember.getContainingClass();
    String text = MessageFormat.format("Access static ''{1}.{0}'' via class ''{2}'' reference",
                                             new Object[]{
                                               HighlightMessageUtil.getSymbolName(myMember, myResult.getSubstitutor()),
                                               HighlightUtil.formatClass(aClass),
                                               HighlightUtil.formatClass(aClass,false),
                                             });
    return text;
  }

  public String getFamilyName() {
    return "Access static via class reference";
  }

  public boolean isAvailable(Project project, Editor editor, PsiFile file) {
    return myExpression != null
           && myExpression.isValid()
           && myExpression.getManager().isInProject(myExpression)
           && myMember != null
           && myMember.isValid()
           && myMember.getContainingClass() != null
           && PsiUtil.isAccessible(myMember.getContainingClass(), myExpression, myMember.getContainingClass());
  }

  public void invoke(Project project, Editor editor, PsiFile file) {
    if (!CodeInsightUtil.prepareFileForWrite(myExpression.getContainingFile())) return;
    try {
      PsiExpression qualifierExpression = myExpression.getQualifierExpression();
      PsiElementFactory factory = file.getManager().getElementFactory();
      if (qualifierExpression instanceof PsiThisExpression &&
          ((PsiThisExpression) qualifierExpression).getQualifier() == null) {
        // this.field -> field
        qualifierExpression.delete();
        if (myExpression.resolve() != myMember) {
          PsiReferenceExpression expr = (PsiReferenceExpression) factory.createExpressionFromText("A.foo", myExpression);
          expr.getQualifierExpression().replace(factory.createReferenceExpression(myMember.getContainingClass()));
          expr.getReferenceNameElement().replace(myExpression);
          myExpression.replace(expr);
        }
      }
      else {
        qualifierExpression.replace(factory.createReferenceExpression(myMember.getContainingClass()));
      }

      QuickFixAction.markDocumentForUndo(file);
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  public boolean startInWriteAction() {
    return true;
  }

}
