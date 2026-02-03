// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.application.options;

import com.intellij.application.options.codeStyle.arrangement.ArrangementSettingsPanel;
import com.intellij.lang.html.HTMLLanguage;
import com.intellij.psi.codeStyle.CodeStyleSettings;

public class HtmlCodeStyleMainPanel extends TabbedLanguageCodeStylePanel {
  protected HtmlCodeStyleMainPanel(CodeStyleSettings currentSettings, CodeStyleSettings settings) {
    super(HTMLLanguage.INSTANCE, currentSettings, settings);
  }

  @Override
  protected void initTabs(CodeStyleSettings settings) {
    addIndentOptionsTab(settings);
    addTab(new CodeStyleHtmlPanel(settings));
    addTab(new ArrangementSettingsPanel(settings, HTMLLanguage.INSTANCE));
    addTab(new GenerationCodeStylePanel(settings, HTMLLanguage.INSTANCE));
  }
}
