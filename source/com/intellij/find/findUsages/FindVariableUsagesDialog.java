
package com.intellij.find.findUsages;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.search.SearchScopeCache;

import javax.swing.*;

public class FindVariableUsagesDialog extends FindUsagesDialog {

  public FindVariableUsagesDialog(PsiElement element, Project project, FindUsagesOptions findUsagesOptions,
                                  boolean toShowInNewTab, boolean isShowInNewTabEnabled, boolean isSingleFile){
    super(element, project, findUsagesOptions, toShowInNewTab, isShowInNewTabEnabled, isSingleFile);
  }

  public JComponent getPreferredFocusedControl() {
    return myCbToSkipResultsWhenOneUsage;
  }

  public FindUsagesOptions getShownOptions(){
    FindUsagesOptions options = new FindUsagesOptions(myProject, SearchScopeCache.getInstance(myProject));
    options.clear();
    options.isReadAccess = false;
    options.isWriteAccess = false;
    options.isUsages = true;
    return options;
  }

  public void calcFindUsagesOptions(FindUsagesOptions options) {
    super.calcFindUsagesOptions(options);

    options.isReadAccess = true;
    options.isWriteAccess = true;
  }

  protected JPanel createFindWhatPanel(){
    return null;
  }

  protected JPanel createAllOptionsPanel() {
    return getPsiElement() instanceof PsiField ? super.createAllOptionsPanel() : createUsagesOptionsPanel();
  }

  protected void update() {
    setOKActionEnabled(true);
  }
}