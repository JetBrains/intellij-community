/**
 * @author cdr
 */
package com.intellij.codeInsight.generation.actions;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.generation.OverrideImplementUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiSuperMethodUtil;
import com.intellij.util.IncorrectOperationException;

public class GenerateSuperMethodCallHandler implements CodeInsightActionHandler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.generation.actions.GenerateSuperMethodCallHandler");

  public void invoke(Project project, Editor editor, PsiFile file) {
    PsiMethod method = canInsertSuper(project, editor, file);
    try {
      PsiMethod template = (PsiMethod)method.copy();

      OverrideImplementUtil.setupBody(template, method, method.getContainingClass());
      PsiStatement superCall = template.getBody().getStatements()[0];
      PsiCodeBlock body = method.getBody();
      if(body.getLBrace() != null) body.addAfter(superCall, body.getLBrace());
      else body.addBefore(superCall, null);
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  public boolean startInWriteAction() {
    return true;
  }

  public static PsiMethod canInsertSuper(Project project, Editor editor, PsiFile file) {
    PsiDocumentManager.getInstance(project).commitAllDocuments();

    int offset = editor.getCaretModel().getOffset();
    PsiElement element = file.findElementAt(offset);
    if (element == null) return null;
    PsiCodeBlock codeBlock = PsiTreeUtil.getParentOfType(element, PsiCodeBlock.class);
    if (codeBlock == null) return null;
    if (!(codeBlock.getParent() instanceof PsiMethod)) return null;
    PsiMethod method = (PsiMethod)codeBlock.getParent();
    PsiMethod[] superMethods = PsiSuperMethodUtil.findSuperMethods(method);
    if (superMethods.length == 0) return null;
    //PsiStatement[] statements = codeBlock.getStatements();
    //if (statements.length == 0) return method;
    //PsiStatement firstStatement = statements[0];
    //if (firstStatement instanceof PsiMethodCallExpression
    //    && ((PsiMethodCallExpression)firstStatement).getMethodExpression().getQualifierExpression() instanceof PsiSuperExpression) {
    //  return null;
    //}
    //return offset < firstStatement.getTextOffset() ? method : null;

    return method;
  }
}