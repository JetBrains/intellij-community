/**
 * created at Sep 17, 2001
 * @author Jeka
 */
package com.intellij.refactoring.move.moveClassesOrPackages;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.usageView.UsageViewBundle;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.usageView.UsageViewUtil;
import org.jetbrains.annotations.NotNull;

class MoveClassesOrPackagesViewDescriptor implements UsageViewDescriptor {
  private PsiElement[] myPsiElements;
  private final boolean mySearchInComments;
  private final boolean mySearchInNonJavaFiles;
  private String myNewParentPackageName;
  private String myProcessedElementsHeader;
  private String myCodeReferencesText;
  private final String myHelpID;

  public MoveClassesOrPackagesViewDescriptor(PsiElement[] psiElements,
                                             boolean isSearchInComments,
                                             boolean searchInNonJavaFiles,
                                             String targetName) {
    myPsiElements = psiElements;
    mySearchInComments = isSearchInComments;
    mySearchInNonJavaFiles = searchInNonJavaFiles;
    myNewParentPackageName = targetName;
    if (psiElements.length == 1) {
      myProcessedElementsHeader = StringUtil.capitalize(
        RefactoringBundle.message("move.single.element.elements.header", UsageViewUtil.getType(psiElements[0]), myNewParentPackageName));
      myCodeReferencesText = RefactoringBundle.message("references.in.code.to.0.1", UsageViewUtil.getType(psiElements[0]), UsageViewUtil.getLongName(psiElements[0]));
    }
    else {
      if (psiElements[0] instanceof PsiClass) {
        myProcessedElementsHeader = StringUtil.capitalize(RefactoringBundle.message("move.classes.elements.header", myNewParentPackageName));
      }
      else if (psiElements[0] instanceof PsiDirectory){
        myProcessedElementsHeader =
          StringUtil.capitalize(RefactoringBundle.message("move.packages.elements.header", myNewParentPackageName));
      }
      myCodeReferencesText = RefactoringBundle.message("references.found.in.code");
    }
    myHelpID = HelpID.getMoveHelpID(psiElements[0]);
  }

  @NotNull
  public PsiElement[] getElements() {
    return myPsiElements;
  }

  public String getProcessedElementsHeader() {
    return myProcessedElementsHeader;
  }

  public String getCodeReferencesText(int usagesCount, int filesCount) {
    return myCodeReferencesText + UsageViewBundle.getReferencesString(usagesCount, filesCount);
  }

  public String getCommentReferencesText(int usagesCount, int filesCount) {
    return RefactoringBundle.message("comments.elements.header",
                                UsageViewBundle.getOccurencesString(usagesCount, filesCount));
  }

}
