package com.intellij.refactoring.rename;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.util.ConflictsUtil;
import com.intellij.refactoring.util.RefactoringUIUtil;

/**
 * @author dsl
 */
public class ClassHidesUnqualifiableClassUsageInfo extends UnresolvableCollisionUsageInfo {
  private final PsiClass myHiddenClass;

  public ClassHidesUnqualifiableClassUsageInfo(PsiJavaCodeReferenceElement element, PsiClass renamedClass, PsiClass hiddenClass) {
    super(element, renamedClass);
    myHiddenClass = hiddenClass;
  }

  public String getDescription() {
    final PsiElement container = ConflictsUtil.getContainer(myHiddenClass);
    return RefactoringBundle.message("renamed.class.will.hide.0.in.1", RefactoringUIUtil.getDescription(myHiddenClass, false),
                                     RefactoringUIUtil.getDescription(container, false));
  }
}
