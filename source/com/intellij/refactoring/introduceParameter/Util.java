/*
 * Created by IntelliJ IDEA.
 * User: dsl
 * Date: 06.05.2002
 * Time: 14:38:40
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.refactoring.introduceParameter;

import com.intellij.psi.*;
import com.intellij.psi.jsp.JspAction;
import com.intellij.psi.jsp.JspFile;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.usageView.UsageInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

public class Util {

  public static final void analyzeExpression(PsiExpression expr,
                                             ArrayList localVars, ArrayList classMemberRefs, ArrayList params)
          {

    if (expr instanceof PsiThisExpression || expr instanceof PsiSuperExpression) {
      classMemberRefs.add(new ClassMemberInExprUsageInfo(expr));
    }
    else if (expr instanceof PsiReferenceExpression) {
      final PsiReferenceExpression refExpr = (PsiReferenceExpression) expr;

      PsiElement subj = refExpr.resolve();

      if (subj instanceof PsiParameter) {
        params.add(new ParameterInExprUsageInfo(refExpr));
      }
      else if (subj instanceof PsiLocalVariable) {
        localVars.add(new LocalVariableInExprUsageInfo(refExpr));
      }
      else if (subj instanceof PsiField || subj instanceof PsiMethod) {
        classMemberRefs.add(new ClassMemberInExprUsageInfo(refExpr));
      }

    }

    PsiElement[] children = expr.getChildren();

    for (int i = 0; i < children.length; i++) {
      PsiElement child = children[i];

      if (child instanceof PsiExpression) {
        analyzeExpression((PsiExpression) child, localVars, classMemberRefs, params);
      }
    }
  }

  public static PsiMethod getContainingMethod(PsiElement expr) {
    PsiElement p;

    for (p = expr; p != null; p = p.getParent()) {
      if (p instanceof PsiMethod) return (PsiMethod) p;
      if (p instanceof PsiFile || p instanceof JspAction || p instanceof JspFile) return null;
    }
    return null;
  }

  public static boolean anyFieldsWithGettersPresent(List classMemberRefs) {
    ListIterator it = classMemberRefs.listIterator();

    while (it.hasNext()) {
      UsageInfo usageInfo = (UsageInfo)it.next();

      if(usageInfo.getElement() instanceof PsiReferenceExpression) {
        PsiElement e = ((PsiReferenceExpression)usageInfo.getElement()).resolve();

        if(e instanceof PsiField) {
          PsiField psiField = (PsiField)e;
          PsiMethod getterPrototype = PropertyUtil.generateGetterPrototype(psiField);

          PsiMethod getter = psiField.getContainingClass().findMethodBySignature(getterPrototype, true);

          if(getter != null)
            return true;
        }
      }
    }

    return false;
  }
}
