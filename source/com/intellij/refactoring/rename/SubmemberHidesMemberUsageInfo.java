/*
 * Created by IntelliJ IDEA.
 * User: dsl
 * Date: 05.06.2002
 * Time: 12:43:27
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.refactoring.rename;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.refactoring.util.ConflictsUtil;
import com.intellij.usageView.UsageViewUtil;

public class SubmemberHidesMemberUsageInfo extends UnresolvableCollisionUsageInfo {
  public SubmemberHidesMemberUsageInfo(PsiElement element, PsiElement referencedElement) {
    super(element, referencedElement);
  }

  public String getDescription() {
    StringBuffer buffer = new StringBuffer();

    buffer.append(ConflictsUtil.getDescription(getElement(), true));
    if (!(getElement() instanceof PsiMethod)) {
      buffer.append(" will hide renamed ");
    }
    else {
      buffer.append(" will override renamed ");
    }
    buffer.append(UsageViewUtil.getType(getElement()));
    return ConflictsUtil.capitalize(buffer.toString());
  }
}
