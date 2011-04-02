package com.jetbrains.python.codeInsight.imports;

import com.intellij.application.options.editor.AutoImportOptionsProvider;
import com.intellij.openapi.options.ConfigurationException;
import com.jetbrains.python.codeInsight.PyCodeInsightSettings;

import javax.swing.*;

/**
 * @author yole
 */
public class PyAutoImportOptions implements AutoImportOptionsProvider {
  private JPanel myMainPanel;
  private JRadioButton myRbFromImport;
  private JRadioButton myRbImport;
  private JCheckBox myShowImportPopupCheckBox;

  public JComponent createComponent() {
    return myMainPanel;
  }

  public void reset() {
    final PyCodeInsightSettings settings = PyCodeInsightSettings.getInstance();
    myRbFromImport.setSelected(settings.PREFER_FROM_IMPORT);
    myShowImportPopupCheckBox.setSelected(settings.SHOW_IMPORT_POPUP);
  }

  public boolean isModified() {
    final PyCodeInsightSettings settings = PyCodeInsightSettings.getInstance();
    return settings.PREFER_FROM_IMPORT != myRbFromImport.isSelected() ||
      settings.SHOW_IMPORT_POPUP != myShowImportPopupCheckBox.isSelected();
  }

  public void apply() throws ConfigurationException {
    final PyCodeInsightSettings settings = PyCodeInsightSettings.getInstance();
    settings.PREFER_FROM_IMPORT = myRbFromImport.isSelected();
    settings.SHOW_IMPORT_POPUP = myShowImportPopupCheckBox.isSelected();
  }

  public void disposeUIResources() {
  }
}
