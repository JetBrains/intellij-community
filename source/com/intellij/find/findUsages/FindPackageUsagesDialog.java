
package com.intellij.find.findUsages;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.SearchScopeCache;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.StateRestoringCheckBox;

import javax.swing.*;

public class FindPackageUsagesDialog extends FindUsagesDialog {
  private StateRestoringCheckBox myCbUsages;
  private StateRestoringCheckBox myCbClassesUsages;

  public FindPackageUsagesDialog(PsiElement element,
                                 Project project,
                                 FindUsagesOptions findUsagesOptions,
                                 boolean toShowInNewTab,
                                 boolean isShowInNewTabEnabled,
                                 boolean isSingleFile) {
    super(element, project, findUsagesOptions, toShowInNewTab, isShowInNewTabEnabled, isSingleFile);
  }

  public JComponent getPreferredFocusedControl() {
    return myCbUsages;
  }

  public FindUsagesOptions getShownOptions(){
    FindUsagesOptions options = new FindUsagesOptions(myProject, SearchScopeCache.getInstance(myProject));
    options.clear();
    options.isUsages = true;
    options.isClassesUsages = isToChange(myCbClassesUsages);
    options.isSearchInNonJavaFiles = isToChange(myCbToSearchInNonJavaFiles);
    return options;
  }

  public void calcFindUsagesOptions(FindUsagesOptions options) {
    super.calcFindUsagesOptions(options);

    options.isUsages = isSelected(myCbUsages);
    if (isToChange(myCbClassesUsages)){
      options.isClassesUsages = isSelected(myCbClassesUsages);
    }
    options.isSkipPackageStatements = false;
    options.isSkipImportStatements = false;
  }

  protected JPanel createFindWhatPanel() {
    JPanel findWhatPanel = new JPanel();
    findWhatPanel.setBorder(IdeBorderFactory.createTitledBorder("Find"));
    findWhatPanel.setLayout(new BoxLayout(findWhatPanel, BoxLayout.Y_AXIS));

    myCbUsages = addCheckboxToPanel("Usages", myFindUsagesOptions.isUsages, findWhatPanel, true, 'U');
    myCbClassesUsages = addCheckboxToPanel("Usages of classes and interfaces", myFindUsagesOptions.isClassesUsages, findWhatPanel, true, 'c');

    return findWhatPanel;
  }

  protected void update() {
    if(myCbToSearchInNonJavaFiles != null){
      if (isSelected(myCbUsages)){
        myCbToSearchInNonJavaFiles.makeSelectable();
      }
      else{
        myCbToSearchInNonJavaFiles.makeUnselectable(false);
      }
    }

    boolean hasSelected = isSelected(myCbUsages) || isSelected(myCbClassesUsages);
    setOKActionEnabled(hasSelected);
  }
}