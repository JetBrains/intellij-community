package com.intellij.bash.formatter;

import com.intellij.application.options.TabbedLanguageCodeStylePanel;
import com.intellij.bash.BashLanguage;
import com.intellij.psi.codeStyle.CodeStyleSettings;

public class BashCodeStyleMainPanel  extends TabbedLanguageCodeStylePanel {

  BashCodeStyleMainPanel(CodeStyleSettings currentSettings, CodeStyleSettings settings) {
    super(BashLanguage.INSTANCE, currentSettings, settings);
  }

  @Override
  protected void initTabs(CodeStyleSettings settings) {
    addIndentOptionsTab(settings);
  }
}
