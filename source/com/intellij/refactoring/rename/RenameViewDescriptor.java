
package com.intellij.refactoring.rename;

import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.HelpID;
import com.intellij.usageView.FindUsagesCommand;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.usageView.UsageViewUtil;

import java.util.ArrayList;

class RenameViewDescriptor implements UsageViewDescriptor{
  private final boolean mySearchInComments;
  private final boolean mySearchInNonJavaFiles;
  private UsageInfo[] myUsages;
  private String myProcessedElementsHeader;
  private String myCodeReferencesText;
  private final String myHelpID;
  private FindUsagesCommand myRefreshCommand;
  private ArrayList myElements;

  public RenameViewDescriptor(
      PsiElement primaryElement,
      ArrayList elements,
      ArrayList names,
      boolean isSearchInComments,
      boolean isSearchInNonJavaFiles,
      UsageInfo[] usages,
      FindUsagesCommand refreshCommand) {

    myElements = elements;

    mySearchInComments = isSearchInComments;
    mySearchInNonJavaFiles = isSearchInNonJavaFiles;
    myUsages = usages;
    myRefreshCommand = refreshCommand;

    StringBuffer processedElementsHeader = new StringBuffer();
    StringBuffer codeReferencesText = new StringBuffer();
    codeReferencesText.append("References in code to ");

    for (int i = 0; i < elements.size(); i++) {
      final PsiElement element = (PsiElement)elements.get(i);
      String newName = (String)names.get(i);

      String prefix = "";
      if (element instanceof PsiDirectory/* || element instanceof PsiClass*/){
        String fullName = UsageViewUtil.getLongName(element);
        int lastDot = fullName.lastIndexOf('.');
        if (lastDot >= 0){
          prefix = fullName.substring(0, lastDot + 1);
        }
      }

      processedElementsHeader.append(UsageViewUtil.capitalize(
          UsageViewUtil.getType(element) + " to be renamed to " + prefix + newName));
      codeReferencesText.append(UsageViewUtil.getType(element) + " " + UsageViewUtil.getLongName(element));

      if (i < elements.size() - 1) {
        processedElementsHeader.append(", ");
        codeReferencesText.append(", ");
      }
    }
    codeReferencesText.append(" ");

    myProcessedElementsHeader = processedElementsHeader.toString();
    myCodeReferencesText =  codeReferencesText.toString();
    myHelpID = HelpID.getRenameHelpID(primaryElement);
  }

  public PsiElement[] getElements() {
    return (PsiElement[])myElements.toArray(new PsiElement[myElements.size()]);
  }

  public UsageInfo[] getUsages() {
    return myUsages;
  }

  public void refresh(PsiElement[] elements) {
    for (int i = 0; i < elements.length; i++) {
      PsiElement element = elements[i];
      myElements.set(i, element);
    }

    if (myRefreshCommand != null) {
      myUsages = myRefreshCommand.execute(elements);
    }
  }

  public String getProcessedElementsHeader() {
    return myProcessedElementsHeader;
  }

  public boolean isSearchInText() {
    return mySearchInComments || mySearchInNonJavaFiles;
  }

  public boolean toMarkInvalidOrReadonlyUsages() {
    return true;
  }

  public String getCodeReferencesWord(){
    return REFERENCE_WORD;
  }

  public String getCommentReferencesWord(){
    return OCCURRENCE_WORD;
  }

  public boolean cancelAvailable() {
    return true;
  }

  public String getCodeReferencesText(int usagesCount, int filesCount) {
    return myCodeReferencesText + UsageViewUtil.getUsageCountInfo(usagesCount, filesCount, "reference");
  }

  public String getCommentReferencesText(int usagesCount, int filesCount) {
    return "Occurrences found in comments, strings and non-java files " + UsageViewUtil.getUsageCountInfo(usagesCount, filesCount, "occurrence");
  }

  public boolean isCancelInCommonGroup() {
    return false;
  }

  public String getHelpID() {
    return myHelpID;
  }

  public boolean canRefresh() {
    return myRefreshCommand != null;
  }

  public boolean willUsageBeChanged(UsageInfo usageInfo) {
    return true;
  }

  public boolean canFilterMethods() {
    return true;
  }
}