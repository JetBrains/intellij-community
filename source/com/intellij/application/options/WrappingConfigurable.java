package com.intellij.application.options;

import com.intellij.openapi.options.BaseConfigurable;
import com.intellij.psi.codeStyle.CodeStyleSettings;

import javax.swing.*;

public class WrappingConfigurable extends BaseConfigurable {
  private WrappingPanel myPanel;
  private CodeStyleSettings mySettings;

  public WrappingConfigurable(CodeStyleSettings settings) {
    mySettings = settings;
  }

  public boolean isModified() {
    return myPanel.isModified();
  }

  public JComponent createComponent() {
    myPanel = new WrappingPanel(mySettings);
    return myPanel;
  }

  public String getDisplayName() {
    return "Wrapping";
  }

  public Icon getIcon() {
    return null;
  }

  public void reset() {
    myPanel.reset();
  }

  public void apply() {
    myPanel.apply();
  }

  public void disposeUIResources() {
    if (myPanel != null) {
      myPanel.dispose();
    }
    myPanel = null;
  }

  public String getHelpTopic() {
    return "preferences.sourceCode.wrap";
  }
}