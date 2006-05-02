/**
 * created at Sep 11, 2001
 * @author Jeka
 */
package com.intellij.refactoring.move.moveInner;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.usageView.UsageViewBundle;
import com.intellij.usageView.UsageViewDescriptor;

class MoveInnerViewDescriptor implements UsageViewDescriptor {

  private PsiClass myInnerClass;

  public MoveInnerViewDescriptor(PsiClass innerClass) {
    myInnerClass = innerClass;
  }

  public PsiElement[] getElements() {
    return new PsiElement[] {myInnerClass};
  }

  public String getProcessedElementsHeader() {
    return RefactoringBundle.message("move.inner.class.to.be.moved");
  }

  public String getCodeReferencesText(int usagesCount, int filesCount) {
    return RefactoringBundle.message("references.to.be.changed", UsageViewBundle.getReferencesString(usagesCount, filesCount));
  }

  public String getCommentReferencesText(int usagesCount, int filesCount) {
    return null;
  }

}
