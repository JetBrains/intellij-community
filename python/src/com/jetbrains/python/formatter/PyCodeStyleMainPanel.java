package com.jetbrains.python.formatter;

import com.intellij.application.options.MultiTabLanguageCodeStylePanel;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.jetbrains.python.PythonLanguage;

/**
 * @author Rustam Vishnyakov
 */
public class PyCodeStyleMainPanel extends MultiTabLanguageCodeStylePanel {
  protected PyCodeStyleMainPanel(CodeStyleSettings currentSettings, CodeStyleSettings settings) {
    super(PythonLanguage.getInstance(), currentSettings, settings);
  }

}
