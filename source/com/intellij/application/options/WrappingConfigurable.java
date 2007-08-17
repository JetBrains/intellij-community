package com.intellij.application.options;

import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.psi.codeStyle.CodeStyleSettings;

import javax.swing.*;

public class WrappingConfigurable extends CodeStyleAbstractConfigurable {
  public WrappingConfigurable(CodeStyleSettings settings, CodeStyleSettings cloneSettings) {
    super(settings, cloneSettings, ApplicationBundle.message("title.wrapping"));
  }

  public Icon getIcon() {
    return StdFileTypes.JAVA.getIcon();
  }

  protected CodeStyleAbstractPanel createPanel(final CodeStyleSettings settings) {
    return new WrappingPanel(settings);
  }

  public String getHelpTopic() {
    return "reference.settingsdialog.IDE.globalcodestyle.wrap";
  }
}