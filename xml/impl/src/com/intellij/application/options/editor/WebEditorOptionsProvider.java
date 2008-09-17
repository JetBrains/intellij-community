package com.intellij.application.options.editor;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.xml.XmlBundle;

import javax.swing.*;

/**
 * @author spleaner
 */
public class WebEditorOptionsProvider implements EditorOptionsProvider {
  private JPanel myWholePanel;
  private JCheckBox myAutomaticallyInsertClosingTagCheckBox;
  private JCheckBox myAutomaticallyInsertRequiredAttributesCheckBox;
  private JCheckBox myAutomaticallyStartAttributeAfterCheckBox;

  public String getDisplayName() {
    return XmlBundle.message("web.editor.configuration.title");
  }

  public Icon getIcon() {
    return null;
  }

  public String getHelpTopic() {
    return null;
  }

  public JComponent createComponent() {
    return myWholePanel;
  }

  public boolean isModified() {
    final WebEditorOptions xmlEditorOptions = WebEditorOptions.getInstance();
    return xmlEditorOptions.isAutomaticallyInsertClosingTag() != myAutomaticallyInsertClosingTagCheckBox.isSelected()
        || xmlEditorOptions.isAutomaticallyInsertRequiredAttributes() != myAutomaticallyInsertRequiredAttributesCheckBox.isSelected()
        || xmlEditorOptions.isAutomaticallyStartAttribute() != myAutomaticallyStartAttributeAfterCheckBox.isSelected();
  }

  public void apply() throws ConfigurationException {
    final WebEditorOptions xmlEditorOptions = WebEditorOptions.getInstance();
    xmlEditorOptions.setAutomaticallyInsertClosingTag(myAutomaticallyInsertClosingTagCheckBox.isSelected());
    xmlEditorOptions.setAutomaticallyInsertRequiredAttributes(myAutomaticallyInsertRequiredAttributesCheckBox.isSelected());
    xmlEditorOptions.setAutomaticallyStartAttribute(myAutomaticallyStartAttributeAfterCheckBox.isSelected());
  }

  public void reset() {
    final WebEditorOptions xmlEditorOptions = WebEditorOptions.getInstance();
    myAutomaticallyInsertClosingTagCheckBox.setSelected(xmlEditorOptions.isAutomaticallyInsertClosingTag());
    myAutomaticallyInsertRequiredAttributesCheckBox.setSelected(xmlEditorOptions.isAutomaticallyInsertRequiredAttributes());
    myAutomaticallyStartAttributeAfterCheckBox.setSelected(xmlEditorOptions.isAutomaticallyStartAttribute());
  }

  public void disposeUIResources() {
  }
}
