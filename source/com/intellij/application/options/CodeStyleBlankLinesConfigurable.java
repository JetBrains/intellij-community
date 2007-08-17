package com.intellij.application.options;

import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.psi.codeStyle.CodeStyleSettings;

import javax.swing.*;

public class CodeStyleBlankLinesConfigurable extends CodeStyleAbstractConfigurable {
  public CodeStyleBlankLinesConfigurable(CodeStyleSettings settings, CodeStyleSettings cloneSettings) {
    super(settings, cloneSettings, ApplicationBundle.message("title.blank.lines"));
  }

  protected CodeStyleAbstractPanel createPanel(final CodeStyleSettings settings) {
    return new CodeStyleBlankLinesPanel(settings);
  }

  public Icon getIcon() {
    return StdFileTypes.JAVA.getIcon();
  }

  public String getHelpTopic() {
    return "reference.settingsdialog.IDE.globalcodestyle.blanklines";
  }
}