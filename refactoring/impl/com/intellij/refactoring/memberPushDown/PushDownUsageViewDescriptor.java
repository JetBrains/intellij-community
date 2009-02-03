package com.intellij.refactoring.memberPushDown;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.usageView.UsageViewBundle;
import com.intellij.usageView.UsageViewDescriptor;
import org.jetbrains.annotations.NotNull;

class PushDownUsageViewDescriptor implements UsageViewDescriptor {
  private final PsiClass myClass;
  private final String myProcessedElementsHeader = RefactoringBundle.message("push.down.members.elements.header");

  public PushDownUsageViewDescriptor(PsiClass aClass) {
    myClass = aClass;
  }

  public String getProcessedElementsHeader() {
    return myProcessedElementsHeader;
  }

  @NotNull
  public PsiElement[] getElements() {
    return new PsiElement[]{myClass};
  }

  public String getCodeReferencesText(int usagesCount, int filesCount) {
    return RefactoringBundle.message("classes.to.push.down.members.to", UsageViewBundle.getReferencesString(usagesCount, filesCount));
  }

  public String getCommentReferencesText(int usagesCount, int filesCount) {
    return null;
  }

}
