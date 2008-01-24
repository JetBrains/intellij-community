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
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.util.ConflictsUtil;
import com.intellij.refactoring.util.RefactoringUIUtil;
import com.intellij.usageView.UsageViewUtil;

public class LocalHidesRenamedLocalUsageInfo extends UnresolvableCollisionUsageInfo {
  private final PsiElement myConflictingElement;

  public LocalHidesRenamedLocalUsageInfo(PsiElement element, PsiElement conflictingElement) {
    super(element, null);
    myConflictingElement = conflictingElement;
  }

  public String getDescription() {

    final String descr = RefactoringBundle.message("there.is.already.a.0.it.will.conflict.with.the.renamed.1",
                                                   RefactoringUIUtil.getDescription(myConflictingElement, true),
                                                   UsageViewUtil.getType(getElement()));
    return ConflictsUtil.capitalize(descr);
  }
}
