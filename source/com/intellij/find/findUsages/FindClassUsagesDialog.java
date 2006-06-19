
package com.intellij.find.findUsages;

import com.intellij.find.FindBundle;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiModifier;
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

  public FindClassUsagesDialog(PsiElement element, Project project, FindUsagesOptions findUsagesOptions, FindUsagesManager manager, boolean isSingleFile){
    super(element, project, findUsagesOptions, manager, isSingleFile);
  }

  public JComponent getPreferredFocusedControl() {
    return myCbUsages;
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

    findWhatPanel.setBorder(IdeBorderFactory.createTitledBorder(FindBundle.message("find.what.group")));
    findWhatPanel.setLayout(new BoxLayout(findWhatPanel, BoxLayout.Y_AXIS));

    myCbUsages = addCheckboxToPanel(FindBundle.message("find.what.usages.checkbox"), myFindUsagesOptions.isUsages, findWhatPanel, true);

    PsiClass psiClass = (PsiClass)getPsiElement();
    myCbMethodsUsages = addCheckboxToPanel(FindBundle.message("find.what.methods.usages.checkbox"), myFindUsagesOptions.isMethodsUsages, findWhatPanel, true);

    if (!psiClass.isAnnotationType()) {
      myCbFieldsUsages = addCheckboxToPanel(FindBundle.message("find.what.fields.usages.checkbox"), myFindUsagesOptions.isFieldsUsages, findWhatPanel, true);
      if (psiClass.isInterface()){
        myCbImplementingClasses = addCheckboxToPanel(FindBundle.message("find.what.implementing.classes.checkbox"), myFindUsagesOptions.isImplementingClasses, findWhatPanel, true);
        myCbDerivedInterfaces = addCheckboxToPanel(FindBundle.message("find.what.derived.interfaces.checkbox"), myFindUsagesOptions.isDerivedInterfaces, findWhatPanel, true);
      }
      else if (!psiClass.hasModifierProperty(PsiModifier.FINAL)){
        myCbDerivedClasses = addCheckboxToPanel(FindBundle.message("find.what.derived.classes.checkbox"), myFindUsagesOptions.isDerivedClasses, findWhatPanel, true);
      }
    }
    return findWhatPanel;
  }

  protected void update() {
    if(myCbToSearchForTextOccurences != null){
      if (isSelected(myCbUsages)){
        myCbToSearchForTextOccurences.makeSelectable();
      }
      else{
        myCbToSearchForTextOccurences.makeUnselectable(false);
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