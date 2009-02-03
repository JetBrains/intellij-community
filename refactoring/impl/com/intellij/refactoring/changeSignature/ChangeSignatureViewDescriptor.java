/**
 * created at Sep 17, 2001
 * @author Jeka
 */
package com.intellij.refactoring.changeSignature;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.usageView.UsageViewBundle;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

class ChangeSignatureViewDescriptor implements UsageViewDescriptor {

  private final PsiMethod myMethod;
  private final String myProcessedElementsHeader;

  public ChangeSignatureViewDescriptor(PsiMethod method) {
    myMethod = method;
    myProcessedElementsHeader = StringUtil.capitalize(RefactoringBundle.message("0.to.change.signature", UsageViewUtil.getType(method)));
  }

  @NotNull
  public PsiElement[] getElements() {
    return new PsiElement[] {myMethod};
  }

  public String getProcessedElementsHeader() {
    return myProcessedElementsHeader;
  }

  public String getCodeReferencesText(int usagesCount, int filesCount) {
    return RefactoringBundle.message("references.to.be.changed",
                                     UsageViewBundle.getReferencesString(usagesCount, filesCount));
  }

  public String getCommentReferencesText(int usagesCount, int filesCount) {
    return null;
  }

}
