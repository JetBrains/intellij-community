// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  @Override
  public JComponent createComponent() {
    return myMainPanel;
  }

  @Override
  public void reset() {
    final PyCodeInsightSettings settings = PyCodeInsightSettings.getInstance();
    myRbFromImport.setSelected(settings.PREFER_FROM_IMPORT);
    myRbImport.setSelected(!settings.PREFER_FROM_IMPORT);
    myShowImportPopupCheckBox.setSelected(settings.SHOW_IMPORT_POPUP);
  }

  @Override
  public boolean isModified() {
    final PyCodeInsightSettings settings = PyCodeInsightSettings.getInstance();
    return settings.PREFER_FROM_IMPORT != myRbFromImport.isSelected() ||
      settings.SHOW_IMPORT_POPUP != myShowImportPopupCheckBox.isSelected();
  }

  @Override
  public void apply() throws ConfigurationException {
    final PyCodeInsightSettings settings = PyCodeInsightSettings.getInstance();
    settings.PREFER_FROM_IMPORT = myRbFromImport.isSelected();
    settings.SHOW_IMPORT_POPUP = myShowImportPopupCheckBox.isSelected();
  }

  @Override
  public void disposeUIResources() {
  }
}
