package com.intellij.refactoring.safeDelete;

import com.intellij.psi.*;
import com.intellij.refactoring.safeDelete.usageInfo.SafeDeleteReferenceUsageInfo;
import com.intellij.refactoring.util.ConflictsUtil;
import com.intellij.usageView.UsageInfo;

import java.util.ArrayList;

/**
 * @author dsl
 */
class UsageHolder {
  private final PsiElement myElement;
  private final SafeDeleteReferenceUsageInfo[] myUsages;
  private int myUnsafeUsages = -1;
  private int myNonCodeUsages = -1;

  public UsageHolder(PsiElement element, UsageInfo[] usageInfos) {
    myElement = element;

    ArrayList<SafeDeleteReferenceUsageInfo> elementUsages = new ArrayList<SafeDeleteReferenceUsageInfo>();
    for (int i = 0; i < usageInfos.length; i++) {
      UsageInfo usageInfo = usageInfos[i];
      if(usageInfo instanceof SafeDeleteReferenceUsageInfo) {
        final SafeDeleteReferenceUsageInfo referenceUsageInfo = (SafeDeleteReferenceUsageInfo) usageInfo;
        if(referenceUsageInfo.getReferencedElement() == myElement) {
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
      for (int i = 0; i < myUsages.length; i++) {
        SafeDeleteReferenceUsageInfo usage = myUsages[i];
        if(usage.isNonCodeUsage) { nonCodeUsages++; }
      }
      myNonCodeUsages = nonCodeUsages;
    }
    return myNonCodeUsages;
  }

  public int getUnsafeUsagesNumber() {
    if(myUnsafeUsages < 0) {
      int nonSafeUsages = 0;
      for (int i = 0; i < myUsages.length; i++) {
        SafeDeleteReferenceUsageInfo usage = myUsages[i];
        if(!usage.isSafeDelete()) { nonSafeUsages++; }
      }
      myUnsafeUsages = nonSafeUsages;
    }
    return myUnsafeUsages;
  }

  public String getDescription() {
    final int nonCodeUsages = getNonCodeUsagesNumber();
    final int nonSafeUsages = getUnsafeUsagesNumber();

    if(nonSafeUsages == 0) return null;

    StringBuffer buffer = new StringBuffer();
    buffer.append(ConflictsUtil.getDescription(myElement, true));
    buffer.append(" has ");
    if(nonCodeUsages == nonSafeUsages) {
      buffer.append(nonCodeUsages + getCorrectForm(nonCodeUsages, " usage", " usages"));
      buffer.append(" in strings, comments, or non-Java files.");
    }
    else {
      buffer.append(nonSafeUsages + getCorrectForm(nonSafeUsages, " usage", " usages"));
      buffer.append(" that " + getCorrectForm(nonSafeUsages, "is", "are") + " not safe to delete.");
      if(nonCodeUsages > 0) {
        buffer.append(" Of those, ");
        buffer.append(nonCodeUsages);
        buffer.append(getCorrectForm(nonCodeUsages, " usage is", " usages are"));
        buffer.append(" in strings, comments, or non-Java files.");
      }
    }
    return buffer.toString();
  }

  private String getCorrectForm(final int itemsNumber, String singleForm, String multipleForm) {
    if (itemsNumber == 1) {
      return singleForm;
    }
    else {
      return multipleForm;
    }
  }

}
