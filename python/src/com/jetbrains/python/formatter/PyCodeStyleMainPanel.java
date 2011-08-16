package com.jetbrains.python.formatter;

import com.intellij.application.options.TabbedLanguageCodeStylePanel;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.jetbrains.python.PythonLanguage;

/**
 * @author Rustam Vishnyakov
 */
public class PyCodeStyleMainPanel extends TabbedLanguageCodeStylePanel {
  protected PyCodeStyleMainPanel(CodeStyleSettings currentSettings, CodeStyleSettings settings) {
    super(PythonLanguage.getInstance(), currentSettings, settings);
  }

}
