/**
 * created at Nov 24, 2001
 * @author Jeka
 */
package com.intellij.refactoring.migration;

import com.intellij.psi.PsiElement;
import com.intellij.refactoring.HelpID;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.usageView.UsageViewUtil;

class MigrationUsagesViewDescriptor implements UsageViewDescriptor {
  private boolean isSearchInComments;
  private MigrationMap myMigrationMap;
  private UsageInfo[] myUsages;

  public MigrationUsagesViewDescriptor(MigrationMap migrationMap, boolean isSearchInComments, UsageInfo[] usages) {
    myMigrationMap = migrationMap;
    this.isSearchInComments = isSearchInComments;
    myUsages = usages;
  }

  public MigrationMap getMigrationMap() {
    return myMigrationMap;
  }

  public PsiElement[] getElements() {
    return PsiElement.EMPTY_ARRAY;
  }

  public UsageInfo[] getUsages() {
    return myUsages;
  }

  public void refresh(PsiElement[] elements) {
    //No elements => no refresh
  }

  public String getProcessedElementsHeader() {
    return null;
  }

  public boolean isSearchInText() {
    return isSearchInComments;
  }

  public boolean toMarkInvalidOrReadonlyUsages() {
    return true;
  }

  public String getCodeReferencesWord(){
    return REFERENCE_WORD;
  }

  public String getCommentReferencesWord(){
    return null;
  }

  public boolean cancelAvailable() {
    return true;
  }

  public String getCodeReferencesText(int usagesCount, int filesCount) {
    return "References in code to elements from migration map \"" + myMigrationMap.getName() + "\" "
      + UsageViewUtil.getUsageCountInfo(usagesCount, filesCount, "reference");
  }

  public String getCommentReferencesText(int usagesCount, int filesCount) {
    return null;
  }

  public String getInfo() {
    return "Press the \"Do Migrate\" button at the bottom of the search results panel\n" +
      "to migrate using the migration map \"" + myMigrationMap.getName() + "\"\n";
  }

  public boolean isCancelInCommonGroup() {
    return false;
  }

  public String getHelpID() {
    return HelpID.MIGRATION;
  }

  public boolean canRefresh() {
    return false;
  }

  public boolean willUsageBeChanged(UsageInfo usageInfo) {
    return true;
  }

  public boolean canFilterMethods() {
    return true;
  }
}
