/*
 * Created by IntelliJ IDEA.
 * User: dsl
 * Date: 05.06.2002
 * Time: 13:38:29
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.refactoring.rename;

import com.intellij.psi.PsiElement;
import com.intellij.refactoring.util.ConflictsUtil;
import com.intellij.usageView.UsageViewUtil;

public class LocalHidesRenamedLocalUsageInfo extends UnresolvableCollisionUsageInfo {
  private final PsiElement myConflictingElement;

  public LocalHidesRenamedLocalUsageInfo(PsiElement element, PsiElement conflictingElement) {
    super(element, null);
    myConflictingElement = conflictingElement;
  }

  public String getDescription() {
    StringBuffer buffer = new StringBuffer();

    buffer.append("There is already a ");
    buffer.append(ConflictsUtil.getDescription(myConflictingElement, true));
    buffer.append(". It will conflict with the renamed ");
    buffer.append(UsageViewUtil.getType(getElement()));

    return ConflictsUtil.capitalize(buffer.toString());
  }
}
