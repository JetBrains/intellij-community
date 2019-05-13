/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.formatter;

import com.intellij.application.options.CodeStyleAbstractPanel;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBRadioButton;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.PythonLanguage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author Mikhail Golubev
 */
public class PyImportsCodeStylePanel extends CodeStyleAbstractPanel {
  private JBCheckBox mySortImports;
  private JBCheckBox mySortNamesInFromImports;
  private JBCheckBox mySortImportsByTypeFirst;
  private JPanel myRootPanel;
  private JBCheckBox mySortCaseInsensitively;
  private JBRadioButton myDoNothingWithFromImports;
  private JBRadioButton myJoinFromImportsWithSameSource;
  private JBRadioButton myAlwaysSplitFromImports;

  public PyImportsCodeStylePanel(@NotNull CodeStyleSettings settings) {
    super(PythonLanguage.getInstance(), null, settings);
    addPanelToWatch(myRootPanel);

    mySortImports.addActionListener(e -> {
      final boolean sortingEnabled = mySortImports.isSelected();
      mySortNamesInFromImports.setEnabled(sortingEnabled);
      mySortImportsByTypeFirst.setEnabled(sortingEnabled);
      mySortCaseInsensitively.setEnabled(sortingEnabled);
    });
  }

  @Override
  protected String getTabTitle() {
    return PyBundle.message("formatter.imports.panel.title");
  }

  @NotNull
  @Override
  protected FileType getFileType() {
    return PythonFileType.INSTANCE;
  }

  @Nullable
  @Override
  protected EditorHighlighter createHighlighter(EditorColorsScheme scheme) {
    return null;
  }

  @Nullable
  @Override
  protected String getPreviewText() {
    return null;
  }

  @Override
  protected int getRightMargin() {
    return 0;
  }

  @Override
  public void apply(CodeStyleSettings settings) throws ConfigurationException {
    final PyCodeStyleSettings pySettings = settings.getCustomSettings(PyCodeStyleSettings.class);

    pySettings.OPTIMIZE_IMPORTS_SORT_IMPORTS = mySortImports.isSelected();
    pySettings.OPTIMIZE_IMPORTS_SORT_NAMES_IN_FROM_IMPORTS = mySortNamesInFromImports.isSelected();
    pySettings.OPTIMIZE_IMPORTS_SORT_BY_TYPE_FIRST = mySortImportsByTypeFirst.isSelected();
    pySettings.OPTIMIZE_IMPORTS_JOIN_FROM_IMPORTS_WITH_SAME_SOURCE = myJoinFromImportsWithSameSource.isSelected();
    pySettings.OPTIMIZE_IMPORTS_ALWAYS_SPLIT_FROM_IMPORTS = myAlwaysSplitFromImports.isSelected();
    pySettings.OPTIMIZE_IMPORTS_CASE_INSENSITIVE_ORDER = mySortCaseInsensitively.isSelected();
  }

  @Override
  public boolean isModified(CodeStyleSettings settings) {
    final PyCodeStyleSettings pySettings = settings.getCustomSettings(PyCodeStyleSettings.class);

    return mySortImports.isSelected() != pySettings.OPTIMIZE_IMPORTS_SORT_IMPORTS ||
           mySortNamesInFromImports.isSelected() != pySettings.OPTIMIZE_IMPORTS_SORT_NAMES_IN_FROM_IMPORTS ||
           mySortImportsByTypeFirst.isSelected() != pySettings.OPTIMIZE_IMPORTS_SORT_BY_TYPE_FIRST ||
           myJoinFromImportsWithSameSource.isSelected() != pySettings.OPTIMIZE_IMPORTS_JOIN_FROM_IMPORTS_WITH_SAME_SOURCE ||
           myAlwaysSplitFromImports.isSelected() != pySettings.OPTIMIZE_IMPORTS_ALWAYS_SPLIT_FROM_IMPORTS ||
           mySortCaseInsensitively.isSelected() != pySettings.OPTIMIZE_IMPORTS_CASE_INSENSITIVE_ORDER;
  }

  @Nullable
  @Override
  public JComponent getPanel() {
    return myRootPanel;
  }

  @Override
  protected void resetImpl(CodeStyleSettings settings) {
    final PyCodeStyleSettings pySettings = settings.getCustomSettings(PyCodeStyleSettings.class);

    mySortImports.setSelected(pySettings.OPTIMIZE_IMPORTS_SORT_IMPORTS);
    mySortNamesInFromImports.setSelected(pySettings.OPTIMIZE_IMPORTS_SORT_NAMES_IN_FROM_IMPORTS);
    mySortNamesInFromImports.setEnabled(mySortImports.isSelected());
    mySortImportsByTypeFirst.setSelected(pySettings.OPTIMIZE_IMPORTS_SORT_BY_TYPE_FIRST);
    mySortImportsByTypeFirst.setEnabled(mySortImports.isSelected());
    mySortCaseInsensitively.setSelected(pySettings.OPTIMIZE_IMPORTS_CASE_INSENSITIVE_ORDER);
    
    if (pySettings.OPTIMIZE_IMPORTS_JOIN_FROM_IMPORTS_WITH_SAME_SOURCE) {
      myJoinFromImportsWithSameSource.setSelected(true);  
    }
    else if (pySettings.OPTIMIZE_IMPORTS_ALWAYS_SPLIT_FROM_IMPORTS) {
      myAlwaysSplitFromImports.setSelected(true);
    }
    else {
      myDoNothingWithFromImports.setSelected(true);
    }
  }
}
