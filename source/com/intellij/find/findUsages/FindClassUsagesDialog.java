
package com.intellij.find.findUsages;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.search.SearchScopeCache;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.StateRestoringCheckBox;

import javax.swing.*;

public class FindClassUsagesDialog extends FindUsagesDialog {
  private StateRestoringCheckBox myCbUsages;
  private StateRestoringCheckBox myCbMethodsUsages;
  private StateRestoringCheckBox myCbFieldsUsages;
  private StateRestoringCheckBox myCbImplementingClasses;
  private StateRestoringCheckBox myCbDerivedInterfaces;
  private StateRestoringCheckBox myCbDerivedClasses;

  public FindClassUsagesDialog(PsiElement element, Project project, FindUsagesOptions findUsagesOptions, boolean toShowInNewTab, boolean isShowInNewTabEnabled, boolean isSingleFile){
    super(element, project, findUsagesOptions, toShowInNewTab, isShowInNewTabEnabled, isSingleFile);
  }

  public JComponent getPreferredFocusedControl() {
    return myCbUsages;
  }

  public FindUsagesOptions getShownOptions(){
    FindUsagesOptions options = new FindUsagesOptions(myProject, SearchScopeCache.getInstance(myProject));
    options.clear();
    options.isUsages = isToChange(myCbUsages);
    options.isMethodsUsages = isToChange(myCbMethodsUsages);
    options.isFieldsUsages = isToChange(myCbFieldsUsages);
    options.isDerivedClasses = isToChange(myCbDerivedClasses);
    options.isImplementingClasses = isToChange(myCbImplementingClasses);
    options.isDerivedInterfaces = isToChange(myCbDerivedInterfaces);
    options.isSearchInNonJavaFiles = isToChange(myCbToSearchInNonJavaFiles);
    return options;
  }

  public void calcFindUsagesOptions(FindUsagesOptions options) {
    super.calcFindUsagesOptions(options);

    if (isToChange(myCbUsages)){
      options.isUsages = isSelected(myCbUsages);
    }
    if (isToChange(myCbMethodsUsages)){
      options.isMethodsUsages = isSelected(myCbMethodsUsages);
    }
    if (isToChange(myCbFieldsUsages)){
      options.isFieldsUsages = isSelected(myCbFieldsUsages);
    }
    if (isToChange(myCbDerivedClasses)){
      options.isDerivedClasses = isSelected(myCbDerivedClasses);
    }
    if (isToChange(myCbImplementingClasses)){
      options.isImplementingClasses = isSelected(myCbImplementingClasses);
    }
    if (isToChange(myCbDerivedInterfaces)){
      options.isDerivedInterfaces = isSelected(myCbDerivedInterfaces);
    }
    options.isSkipImportStatements = false;
    options.isCheckDeepInheritance = true;
    options.isIncludeInherited = false;
  }

  protected JPanel createFindWhatPanel() {
    JPanel findWhatPanel = new JPanel();

    findWhatPanel.setBorder(IdeBorderFactory.createTitledBorder("Find"));
    findWhatPanel.setLayout(new BoxLayout(findWhatPanel, BoxLayout.Y_AXIS));

    myCbUsages = addCheckboxToPanel("Usages", myFindUsagesOptions.isUsages, findWhatPanel, true, 'U');

    PsiClass psiClass = (PsiClass)getPsiElement();
    myCbMethodsUsages = addCheckboxToPanel("Usages of methods", myFindUsagesOptions.isMethodsUsages, findWhatPanel, true, 'm');

    if (!psiClass.isAnnotationType()) {
      myCbFieldsUsages = addCheckboxToPanel("Usages of fields", myFindUsagesOptions.isFieldsUsages, findWhatPanel, true, 'f');
      if (psiClass.isInterface()){
        myCbImplementingClasses = addCheckboxToPanel("Implementing classes", myFindUsagesOptions.isImplementingClasses, findWhatPanel, true, 'I');
        myCbDerivedInterfaces = addCheckboxToPanel("Derived interfaces", myFindUsagesOptions.isDerivedInterfaces, findWhatPanel, true, 'D');
      }
      else if (!psiClass.hasModifierProperty(PsiModifier.FINAL)){
        myCbDerivedClasses = addCheckboxToPanel("Derived classes", myFindUsagesOptions.isDerivedClasses, findWhatPanel, true, 'D');
      }
    }
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

    boolean hasSelected = isSelected(myCbUsages) ||
      isSelected(myCbFieldsUsages) ||
      isSelected(myCbMethodsUsages) ||
      isSelected(myCbImplementingClasses) ||
      isSelected(myCbDerivedInterfaces) ||
      isSelected(myCbDerivedClasses);
    setOKActionEnabled(hasSelected);
  }

}