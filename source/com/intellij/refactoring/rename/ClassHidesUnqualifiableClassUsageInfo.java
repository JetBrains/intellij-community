package com.intellij.refactoring.rename;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.refactoring.util.ConflictsUtil;

/**
 * @author dsl
 */
public class ClassHidesUnqualifiableClassUsageInfo extends UnresolvableCollisionUsageInfo {
  private final PsiClass myHiddenClass;
  private PsiClass myRenamedClass;

  public ClassHidesUnqualifiableClassUsageInfo(PsiJavaCodeReferenceElement element, PsiClass renamedClass, PsiClass hiddenClass) {
    super(element, renamedClass);
    myRenamedClass = renamedClass;
    myHiddenClass = hiddenClass;
  }

  public String getDescription() {
    final PsiElement container = ConflictsUtil.getContainer(myHiddenClass);
    return "Renamed class will hide " + ConflictsUtil.getDescription(myHiddenClass, false) + " in " +
           ConflictsUtil.getDescription(container, false);
  }
}
