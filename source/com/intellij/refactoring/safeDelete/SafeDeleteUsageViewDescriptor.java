package com.intellij.refactoring.safeDelete;

import com.intellij.psi.PsiElement;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.ui.UsageViewDescriptorAdapter;
import com.intellij.usageView.FindUsagesCommand;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewBundle;

/**
 * @author dsl
 */
public class SafeDeleteUsageViewDescriptor extends UsageViewDescriptorAdapter {
  private final PsiElement[] myElementsToDelete;
  private final SafeDeleteProcessor myProcessor;

  public SafeDeleteUsageViewDescriptor(UsageInfo[] usages,
                                       FindUsagesCommand refreshCommand,
                                       PsiElement[] elementsToDelete,
                                       SafeDeleteProcessor processor) {
    super(usages, refreshCommand);
    myElementsToDelete = elementsToDelete;
    myProcessor = processor;
  }

  public PsiElement[] getElements() {
    return myElementsToDelete;
  }

  public void refresh(PsiElement[] elements) {
    myProcessor.setElements(elements);
    if (myRefreshCommand != null) {
      myUsages = myRefreshCommand.execute(elements);
    }
  }

  public String getProcessedElementsHeader() {
    return RefactoringBundle.message("items.to.be.deleted");
  }

  public boolean isSearchInText() {
    return true;
  }

  public boolean toMarkInvalidOrReadonlyUsages() {
    return true;
  }

  public String getCodeReferencesWord() {
    return RefactoringBundle.message("usageView.occurences.string");
  }

  public String getCommentReferencesWord() {
    return RefactoringBundle.message("usageView.occurences.string");
  }

  public boolean isCancelInCommonGroup() {
    return false;
  }

  public boolean canRefresh() {
    return true;
  }

  public boolean willUsageBeChanged(UsageInfo usageInfo) {
    return !usageInfo.isNonCodeUsage;
  }

  public String getCodeReferencesText(int usagesCount, int filesCount) {
    return RefactoringBundle.message("references.in.code", UsageViewBundle.getReferencesString(usagesCount, filesCount));
  }

  public String getCommentReferencesText(int usagesCount, int filesCount) {
    return RefactoringBundle.message("safe.delete.comment.occurences.header",
                                     UsageViewBundle.getOccurencesString(usagesCount, filesCount));
  }
}
