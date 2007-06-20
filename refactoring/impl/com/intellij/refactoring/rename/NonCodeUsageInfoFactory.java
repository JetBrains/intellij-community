package com.intellij.refactoring.rename;

import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.refactoring.util.NonCodeUsageInfo;
import com.intellij.psi.PsiElement;
import com.intellij.usageView.UsageInfo;

public class NonCodeUsageInfoFactory implements RefactoringUtil.UsageInfoFactory {
  private final PsiElement myElement;
  private final String myStringToReplace;

  public NonCodeUsageInfoFactory(final PsiElement element, final String stringToReplace) {
    myElement = element;
    myStringToReplace = stringToReplace;
  }

  public UsageInfo createUsageInfo(PsiElement usage, int startOffset, int endOffset) {
    int start = usage.getTextRange().getStartOffset();
    return NonCodeUsageInfo.create(usage.getContainingFile(), start + startOffset, start + endOffset, myElement, myStringToReplace);
  }
}