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
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.util.ConflictsUtil;
import com.intellij.refactoring.util.RefactoringUIUtil;
import com.intellij.usageView.UsageViewUtil;

public class SubmemberHidesMemberUsageInfo extends UnresolvableCollisionUsageInfo {
  public SubmemberHidesMemberUsageInfo(PsiElement element, PsiElement referencedElement) {
    super(element, referencedElement);
  }

  public String getDescription() {
    String descr;
    if (!(getElement() instanceof PsiMethod)) {
      descr = RefactoringBundle.message("0.will.hide.renamed.1",
                                        RefactoringUIUtil.getDescription(getElement(), true),
                                        UsageViewUtil.getType(getElement()));
    }
    else {
      descr = RefactoringBundle.message("0.will.override.renamed.1",
                                        RefactoringUIUtil.getDescription(getElement(), true),
                                        UsageViewUtil.getType(getElement()));
    }
    return ConflictsUtil.capitalize(descr);
  }
}
