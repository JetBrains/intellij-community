package com.intellij.codeInsight.actions;

import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.project.Project;
import com.intellij.ide.util.PropertiesComponent;

import javax.swing.*;

public class ReformatFilesDialog extends DialogWrapper {
  private JPanel myPanel;
  private JCheckBox myOptimizeImports;

  public ReformatFilesDialog(Project project) {
    super(project, true);
    setTitle("Reformat Files");
    myOptimizeImports.setSelected(isOptmizeImportsOptionOn());
    init();
  }

  protected JComponent createCenterPanel() {
    return myPanel;
  }

  public boolean optimizeImports(){
    return myOptimizeImports.isSelected();
  }

  protected void doOKAction() {
    PropertiesComponent.getInstance().setValue(LayoutCodeDialog.OPTIMIZE_IMPORTS_KEY, myOptimizeImports.isSelected() ? "true" : "false");
    super.doOKAction();
  }

  private boolean isOptmizeImportsOptionOn() {
    return "true".equals(PropertiesComponent.getInstance().getValue(LayoutCodeDialog.OPTIMIZE_IMPORTS_KEY));
  }

}
