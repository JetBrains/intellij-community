// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.application.options;

import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings.IndentOptions;
import com.intellij.psi.formatter.xml.HtmlCodeStyleSettings;
import com.intellij.xml.XmlBundle;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class HtmlIndentOptionsEditor extends SmartIndentOptionsEditor {

  private JCheckBox myUniformIndentCheckBox;

  @Override
  protected void addComponents() {
    super.addComponents();
    myUniformIndentCheckBox = new JCheckBox(XmlBundle.message("checkbox.uniform.indent"));
    add(myUniformIndentCheckBox);
  }

  @Override
  public boolean isModified(CodeStyleSettings settings, IndentOptions options) {
    return isFieldModified(myUniformIndentCheckBox, settings.getCustomSettings(HtmlCodeStyleSettings.class).HTML_UNIFORM_INDENT)
           || super.isModified(settings, options);
  }

  @Override
  public void apply(CodeStyleSettings settings, IndentOptions options) {
    super.apply(settings, options);
    settings.getCustomSettings(HtmlCodeStyleSettings.class).HTML_UNIFORM_INDENT = myUniformIndentCheckBox.isSelected();
  }

  @Override
  public void reset(@NotNull CodeStyleSettings settings, @NotNull IndentOptions options) {
    super.reset(settings, options);
    myUniformIndentCheckBox.setSelected(settings.getCustomSettings(HtmlCodeStyleSettings.class).HTML_UNIFORM_INDENT);
  }

  @Override
  public void setEnabled(boolean enabled) {
    myUniformIndentCheckBox.setEnabled(enabled);
    super.setEnabled(enabled);
  }
}
