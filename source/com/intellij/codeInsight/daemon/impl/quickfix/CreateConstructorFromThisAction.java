/*
 * @author ven
 */
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.util.PsiTreeUtil;

public class CreateConstructorFromThisAction extends CreateConstructorFromThisOrSuperAction {

  public CreateConstructorFromThisAction(PsiMethodCallExpression methodCall) {
    super(methodCall);
  }

  protected String getSyntheticMethodName() {
    return "this";
  }

  protected PsiClass[] getTargetClasses(PsiElement element) {
    PsiElement e = element;
    do {
      e = PsiTreeUtil.getParentOfType(e, PsiClass.class);
    } while (e instanceof PsiTypeParameter);
    return e != null && e.isValid() && e.getManager().isInProject(e) ? new PsiClass[]{(PsiClass) e} : null;
  }

  public String getFamilyName() {
    return "Create Constructor From this() Call";
  }
}