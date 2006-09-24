package com.intellij.application.options;

import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.options.BaseConfigurable;
import com.intellij.psi.codeStyle.CodeStyleSettings;

import javax.swing.*;

public class CodeStyleImportsConfigurable extends BaseConfigurable {
  private CodeStyleImportsPanel myPanel;
  private CodeStyleSettings mySettings;

  public CodeStyleImportsConfigurable(CodeStyleSettings settings) {
    mySettings = settings;
  }

  public boolean isModified() {
    return myPanel != null && myPanel.isModified();
  }

  public JComponent createComponent() {
    myPanel = new CodeStyleImportsPanel(mySettings);
    return myPanel;
  }

  public String getDisplayName() {
    return ApplicationBundle.message("title.imports");
  }

  public Icon getIcon() {
    return StdFileTypes.JAVA.getIcon();
  }

  public void reset() {
    if (myPanel != null) {
      myPanel.reset();
    }
  }

  public void apply() {
    if (myPanel != null) {
      myPanel.apply();
    }
  }

  public void disposeUIResources() {
    myPanel = null;
  }

  public String getHelpTopic() {
    return "preferences.sourceCode.imports";
  }
}