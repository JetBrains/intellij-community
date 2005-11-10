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
import com.intellij.psi.PsiMember;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.usageView.*;
import com.intellij.usageView.UsageViewBundle;

public class MakeMethodOrClassStaticViewDescriptor implements UsageViewDescriptor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.makeMethodStatic.MakeMethodStaticViewDescriptor");

  private PsiMember myMember;
  private UsageInfo[] myUsages;
  private final String myProcessedElementsHeader;

  public MakeMethodOrClassStaticViewDescriptor(PsiMember member,
                                               UsageInfo[] usages) {
    myMember = member;
    myUsages = usages;
    String who = UsageViewUtil.capitalize(UsageViewUtil.getType(myMember));
    myProcessedElementsHeader = RefactoringBundle.message("make.static.elements.header", who);
  }

  public PsiElement[] getElements() {
    return new PsiElement[]{myMember};
  }

  public UsageInfo[] getUsages() {
    return myUsages;
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
    return RefactoringBundle.message("references.to.be.changed", UsageViewBundle.getReferencesString(usagesCount, filesCount));
  }

  public String getCommentReferencesText(int usagesCount, int filesCount) {
    return null;
  }

  public boolean isCancelInCommonGroup() {
    return false;
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
