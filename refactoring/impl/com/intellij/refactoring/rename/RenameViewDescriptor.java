
package com.intellij.refactoring.rename;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.usageView.UsageViewBundle;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.util.ArrayUtil;
import gnu.trove.THashSet;

import java.util.LinkedHashMap;
import java.util.Set;

class RenameViewDescriptor implements UsageViewDescriptor{
  private final boolean mySearchInComments;
  private final boolean mySearchInNonJavaFiles;
  private String myProcessedElementsHeader;
  private String myCodeReferencesText;
  private final String myHelpID;
  private PsiElement[] myElements;

  public RenameViewDescriptor(
    PsiElement primaryElement,
    LinkedHashMap<PsiElement, String> renamesMap,
    boolean isSearchInComments,
    boolean isSearchInNonJavaFiles
  ) {

    myElements = renamesMap.keySet().toArray(new PsiElement[0]);

    mySearchInComments = isSearchInComments;
    mySearchInNonJavaFiles = isSearchInNonJavaFiles;

    Set<String> processedElementsHeaders = new THashSet<String>();
    Set<String> codeReferences = new THashSet<String>();

    for (final PsiElement element : myElements) {
      String newName = renamesMap.get(element);

      String prefix = "";
      if (element instanceof PsiDirectory/* || element instanceof PsiClass*/) {
        String fullName = UsageViewUtil.getLongName(element);
        int lastDot = fullName.lastIndexOf('.');
        if (lastDot >= 0) {
          prefix = fullName.substring(0, lastDot + 1);
        }
      }

      processedElementsHeaders.add(UsageViewUtil.capitalize(
        RefactoringBundle.message("0.to.be.renamed.to.1.2", UsageViewUtil.getType(element), prefix, newName)));
      codeReferences.add(UsageViewUtil.getType(element) + " " + UsageViewUtil.getLongName(element));
    }


    myProcessedElementsHeader = StringUtil.join(processedElementsHeaders.toArray(ArrayUtil.EMPTY_STRING_ARRAY),", ");
    myCodeReferencesText =  RefactoringBundle.message("references.in.code.to.0", StringUtil.join(codeReferences.toArray(ArrayUtil.EMPTY_STRING_ARRAY), ", "));
    myHelpID = HelpID.getRenameHelpID(primaryElement);
  }

  public PsiElement[] getElements() {
    return myElements;
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