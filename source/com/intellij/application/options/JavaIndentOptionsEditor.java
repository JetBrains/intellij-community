package com.intellij.application.options;

import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.psi.codeStyle.CodeStyleSettings;

import javax.swing.*;

/**
 * @author yole
 */
public class JavaIndentOptionsEditor extends SmartIndentOptionsEditor {
  private JTextField myLabelIndent;
  private JLabel myLabelIndentLabel;

  private JCheckBox myLabelIndentAbsolute;
  private JCheckBox myCbDontIndentTopLevelMembers;

  protected void addComponents() {
    super.addComponents();

    myLabelIndent = new JTextField(4);
    add(myLabelIndentLabel = new JLabel(ApplicationBundle.message("editbox.indent.label.indent")), myLabelIndent);

    myLabelIndentAbsolute = new JCheckBox(ApplicationBundle.message("checkbox.indent.absolute.label.indent"));
    add(myLabelIndentAbsolute, true);

    myCbDontIndentTopLevelMembers = new JCheckBox(ApplicationBundle.message("checkbox.do.not.indent.top.level.class.members"));
    add(myCbDontIndentTopLevelMembers);
  }

  public boolean isModified(final CodeStyleSettings settings, final CodeStyleSettings.IndentOptions options) {
    boolean isModified = super.isModified(settings, options);

    isModified |= isFieldModified(myLabelIndent, options.LABEL_INDENT_SIZE);
    isModified |= isFieldModified(myLabelIndentAbsolute, options.LABEL_INDENT_ABSOLUTE);
    isModified |= isFieldModified(myCbDontIndentTopLevelMembers, settings.DO_NOT_INDENT_TOP_LEVEL_CLASS_MEMBERS);

    return isModified;
  }

  public void apply(final CodeStyleSettings settings, final CodeStyleSettings.IndentOptions options) {
    super.apply(settings, options);
    try {
      options.LABEL_INDENT_SIZE = Integer.parseInt(myLabelIndent.getText());
    }
    catch (NumberFormatException e) {
      //stay with default
    }
    options.LABEL_INDENT_ABSOLUTE = myLabelIndentAbsolute.isSelected();
    settings.DO_NOT_INDENT_TOP_LEVEL_CLASS_MEMBERS = myCbDontIndentTopLevelMembers.isSelected();
  }

  public void reset(final CodeStyleSettings settings, final CodeStyleSettings.IndentOptions options) {
    super.reset(settings, options);
    myLabelIndent.setText(Integer.toString(options.LABEL_INDENT_SIZE));
    myLabelIndentAbsolute.setSelected(options.LABEL_INDENT_ABSOLUTE);
    myCbDontIndentTopLevelMembers.setSelected(settings.DO_NOT_INDENT_TOP_LEVEL_CLASS_MEMBERS);
  }

  public void setEnabled(final boolean enabled) {
    super.setEnabled(enabled);
    myLabelIndent.setEnabled(enabled);
    myLabelIndentLabel.setEnabled(enabled);
    myLabelIndentAbsolute.setEnabled(enabled);
  }
}
