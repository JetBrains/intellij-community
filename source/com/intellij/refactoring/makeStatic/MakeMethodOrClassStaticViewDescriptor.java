/*
 * Created by IntelliJ IDEA.
 * User: dsl
 * Date: 16.04.2002
 * Time: 15:54:37
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.refactoring.makeStatic;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMember;
import com.intellij.usageView.FindUsagesCommand;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.usageView.UsageViewUtil;

public class MakeMethodOrClassStaticViewDescriptor implements UsageViewDescriptor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.makeMethodStatic.MakeMethodStaticViewDescriptor");

  private PsiMember myMember;
  private UsageInfo[] myUsages;
  private final String myProcessedElementsHeader;
  private FindUsagesCommand myRefreshCommand;

  public MakeMethodOrClassStaticViewDescriptor(PsiMember member,
                                               UsageInfo[] usages, FindUsagesCommand refreshCommand) {
    myMember = member;
    myUsages = usages;
    myRefreshCommand = refreshCommand;
    String who = UsageViewUtil.capitalize(UsageViewUtil.getType(myMember));
    myProcessedElementsHeader = who + " to be made static";
  }

  public PsiElement[] getElements() {
    return new PsiElement[]{myMember};
  }

  public UsageInfo[] getUsages() {
    return myUsages;
  }


  public void refresh(PsiElement[] elements) {
    if (elements.length == 1 && elements[0] instanceof PsiMethod) {
      myMember = (PsiMethod)elements[0];
    }
    else {
      // should not happen
      LOG.assertTrue(false);
    }
    if (myRefreshCommand != null) {
      myUsages = myRefreshCommand.execute(elements);
    }
  }

  public String getProcessedElementsHeader() {
    return myProcessedElementsHeader;
  }

  public boolean isSearchInText() {
    return false;
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
    return "References to be changed " + UsageViewUtil.getUsageCountInfo(usagesCount, filesCount, "reference");
  }

  public String getCommentReferencesText(int usagesCount, int filesCount) {
    return null;
  }

  public boolean isCancelInCommonGroup() {
    return false;
  }

  public boolean canRefresh() {
    return myRefreshCommand != null;
  }

  public boolean willUsageBeChanged(UsageInfo usageInfo) {
    return true;
  }

  public String getHelpID() {
    return "find.refactoringPreview";
  }

  public boolean canFilterMethods() {
    return true;
  }
}
