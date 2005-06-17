package com.intellij.application.options;

import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.psi.codeStyle.CodeStyleSettings;

import javax.swing.*;

public class JavadocFormatConfigurable extends CodeStyleAbstractConfigurable {
  public JavadocFormatConfigurable(CodeStyleSettings settings, CodeStyleSettings cloneSettings) {
    super(settings, cloneSettings,"JavaDoc");
  }

  protected CodeStyleAbstractPanel createPanel(final CodeStyleSettings settings) {
    return new JavaDocFormattingPanel(settings);
  }

  public Icon getIcon() {
    return StdFileTypes.JAVA.getIcon();
  }

  public String getHelpTopic() {
    return "preferences.sourceCode.javadoc";
  }
}