package com.intellij.refactoring.safeDelete;

import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.SmartPointerManager;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.safeDelete.usageInfo.SafeDeleteReferenceUsageInfo;
import com.intellij.refactoring.util.RefactoringUIUtil;
import com.intellij.usageView.UsageInfo;

import java.util.ArrayList;

/**
 * @author dsl
 */
class UsageHolder {
  private final SmartPsiElementPointer myElementPointer;
  private final SafeDeleteReferenceUsageInfo[] myUsages;
  private int myUnsafeUsages = -1;
  private int myNonCodeUsages = -1;

  public UsageHolder(PsiElement element, UsageInfo[] usageInfos) {
    myElementPointer = SmartPointerManager.getInstance(element.getProject()).createSmartPsiElementPointer(element);

    ArrayList<SafeDeleteReferenceUsageInfo> elementUsages = new ArrayList<SafeDeleteReferenceUsageInfo>();
    for (UsageInfo usageInfo : usageInfos) {
      if (usageInfo instanceof SafeDeleteReferenceUsageInfo) {
        final SafeDeleteReferenceUsageInfo referenceUsageInfo = (SafeDeleteReferenceUsageInfo)usageInfo;
        if (referenceUsageInfo.getReferencedElement() == element) {
          elementUsages.add(referenceUsageInfo);
        }
      }
    }
    myUsages =
    elementUsages.toArray(new SafeDeleteReferenceUsageInfo[elementUsages.size()]);
  }

  public int getNonCodeUsagesNumber() {
    if(myNonCodeUsages < 0) {
      int nonCodeUsages = 0;
      for (SafeDeleteReferenceUsageInfo usage : myUsages) {
        if (usage.isNonCodeUsage) {
          nonCodeUsages++;
        }
      }
      myNonCodeUsages = nonCodeUsages;
    }
    return myNonCodeUsages;
  }

  public int getUnsafeUsagesNumber() {
    if(myUnsafeUsages < 0) {
      int nonSafeUsages = 0;
      for (SafeDeleteReferenceUsageInfo usage : myUsages) {
        if (!usage.isSafeDelete()) {
          nonSafeUsages++;
        }
      }
      myUnsafeUsages = nonSafeUsages;
    }
    return myUnsafeUsages;
  }

  public String getDescription() {
    final int nonCodeUsages = getNonCodeUsagesNumber();
    final int unsafeUsages = getUnsafeUsagesNumber();

    if(unsafeUsages == 0) return null;

    final PsiElement element = myElementPointer.getElement();
    if (unsafeUsages == nonCodeUsages) {
      return RefactoringBundle.message("0.has.1.usages.in.comments.and.strings",
                                       RefactoringUIUtil.getDescription(element, true),
                                       unsafeUsages);
    }

    return RefactoringBundle.message("0.has.1.usages.that.are.not.safe.to.delete.of.those.2",
      RefactoringUIUtil.getDescription(element, true), unsafeUsages, nonCodeUsages);
  }
}
