package com.intellij.find.findUsages;

import com.intellij.find.FindBundle;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.StateRestoringCheckBox;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class FindMethodUsagesDialog extends FindUsagesDialog {
  private StateRestoringCheckBox myCbUsages;
  private StateRestoringCheckBox myCbImplementingMethods;
  private StateRestoringCheckBox myCbOverridingMethods;
  private boolean myHasFindWhatPanel;

  public FindMethodUsagesDialog(PsiElement element, Project project, FindUsagesOptions findUsagesOptions, FindUsagesManager manager, boolean isSingleFile) {
    super(element, project, findUsagesOptions, manager, isSingleFile);
  }

  @Nullable
  public JComponent getPreferredFocusedControl() {
    return myHasFindWhatPanel ? myCbUsages : null;
  }

  public void calcFindUsagesOptions(FindUsagesOptions options) {
    super.calcFindUsagesOptions(options);

    options.isUsages = isSelected(myCbUsages) || !myHasFindWhatPanel;
    if (isToChange(myCbOverridingMethods)) {
      options.isOverridingMethods = isSelected(myCbOverridingMethods);
    }
    if (isToChange(myCbImplementingMethods)) {
      options.isImplementingMethods = isSelected(myCbImplementingMethods);
    }
    options.isCheckDeepInheritance = true;
  }

  protected JPanel createFindWhatPanel() {
    JPanel findWhatPanel = new JPanel();
    findWhatPanel.setBorder(IdeBorderFactory.createTitledBorder(FindBundle.message("find.what.group")));
    findWhatPanel.setLayout(new BoxLayout(findWhatPanel, BoxLayout.Y_AXIS));

    myCbUsages = addCheckboxToPanel(FindBundle.message("find.what.usages.checkbox"), myFindUsagesOptions.isUsages, findWhatPanel, true);

    PsiMethod method = (PsiMethod) getPsiElement();
    PsiClass aClass = method.getContainingClass();
    if (!method.isConstructor() &&
            !method.hasModifierProperty(PsiModifier.STATIC) &&
            !method.hasModifierProperty(PsiModifier.FINAL) &&
            !method.hasModifierProperty(PsiModifier.PRIVATE) &&
            aClass != null &&
            !(aClass instanceof PsiAnonymousClass) &&
            !aClass.hasModifierProperty(PsiModifier.FINAL)) {
      if (method.hasModifierProperty(PsiModifier.ABSTRACT)) {
        myCbImplementingMethods = addCheckboxToPanel(FindBundle.message("find.what.implementing.methods.checkbox"), myFindUsagesOptions.isImplementingMethods, findWhatPanel, true);
      } else {
        myCbOverridingMethods = addCheckboxToPanel(FindBundle.message("find.what.overriding.methods.checkbox"), myFindUsagesOptions.isOverridingMethods, findWhatPanel, true);
      }
    } else {
      myHasFindWhatPanel = false;
      return null; 
    }
    myHasFindWhatPanel = true;
    return findWhatPanel;

    /*if (method.isConstructor() ||
        method.hasModifierProperty(PsiModifier.STATIC) ||
        method.hasModifierProperty(PsiModifier.FINAL) ||
        method.hasModifierProperty(PsiModifier.PRIVATE) ||
        aClass == null ||
        aClass instanceof PsiAnonymousClass ||
        aClass.hasModifierProperty(PsiModifier.FINAL)){
      myHasFindWhatPanel = false;
      return null;
    }
    else{
      myHasFindWhatPanel = true;
      return findWhatPanel;
    }*/
  }

  protected void update() {
    if (!myHasFindWhatPanel) {
      setOKActionEnabled(true);
    } else {

      boolean hasSelected = isSelected(myCbUsages) || isSelected(myCbImplementingMethods) || isSelected(myCbOverridingMethods);
      setOKActionEnabled(hasSelected);
    }
  }
}