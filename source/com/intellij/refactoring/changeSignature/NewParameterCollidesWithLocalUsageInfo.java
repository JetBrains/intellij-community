package com.intellij.refactoring.changeSignature;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.refactoring.rename.UnresolvableCollisionUsageInfo;
import com.intellij.refactoring.util.ConflictsUtil;

/**
 *  @author dsl
 */
public class NewParameterCollidesWithLocalUsageInfo extends UnresolvableCollisionUsageInfo {
  private final PsiElement myConflictingElement;
  private final PsiMethod myMethod;

  public NewParameterCollidesWithLocalUsageInfo(PsiElement element, PsiElement referencedElement,
                                                PsiMethod method) {
    super(element, referencedElement);
    myConflictingElement = referencedElement;
    myMethod = method;
  }

  public String getDescription() {
    StringBuffer buffer = new StringBuffer();

    buffer.append("There is already a ");
    buffer.append(ConflictsUtil.getDescription(myConflictingElement, true));
    buffer.append("in a ");
    buffer.append(ConflictsUtil.getDescription(myMethod, true));
    buffer.append(". It will conflict with the new parameter.");

    return ConflictsUtil.capitalize(buffer.toString());
  }
}
