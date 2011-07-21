package com.jetbrains.python.formatter;

import com.intellij.application.options.MultiTabCodeStyleAbstractPanel;
import com.intellij.lang.Language;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.jetbrains.python.PythonLanguage;

/**
 * @author Rustam Vishnyakov
 */
public class PyCodeStyleMainPanel extends MultiTabCodeStyleAbstractPanel {
  protected PyCodeStyleMainPanel(CodeStyleSettings settings) {
    super(settings);
  }

  @Override
  public Language getDefaultLanguage() {
    return PythonLanguage.getInstance();
  }
}
