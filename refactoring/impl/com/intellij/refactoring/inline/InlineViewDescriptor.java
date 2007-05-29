
package com.intellij.refactoring.inline;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.usageView.UsageViewBundle;
import com.intellij.usageView.UsageViewDescriptor;
import org.jetbrains.annotations.NotNull;

class InlineViewDescriptor implements UsageViewDescriptor{

  private PsiElement myElement;

  public InlineViewDescriptor(PsiElement element) {
    myElement = element;
  }

  @NotNull
  public PsiElement[] getElements() {
    return new PsiElement[] {myElement};
  }

  public String getProcessedElementsHeader() {
    if (myElement instanceof PsiClass) {
      return RefactoringBundle.message("inline.class.elements.header");
    }
    return myElement instanceof PsiMethod ?
           RefactoringBundle.message("inline.method.elements.header") :
           RefactoringBundle.message("inline.field.elements.header");
  }

  public String getCodeReferencesText(int usagesCount, int filesCount) {
    return RefactoringBundle.message("invocations.to.be.inlined", UsageViewBundle.getReferencesString(usagesCount, filesCount));
  }

  public String getCommentReferencesText(int usagesCount, int filesCount) {
    return null;
  }

}