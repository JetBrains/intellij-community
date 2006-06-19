
package com.intellij.find.findUsages;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;

import javax.swing.*;

public class FindVariableUsagesDialog extends FindUsagesDialog {

  public FindVariableUsagesDialog(PsiElement element, Project project, FindUsagesOptions findUsagesOptions,
                                  FindUsagesManager manager, boolean isSingleFile){
    super(element, project, findUsagesOptions, manager, isSingleFile);
  }

  public JComponent getPreferredFocusedControl() {
    return myCbToSkipResultsWhenOneUsage;
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