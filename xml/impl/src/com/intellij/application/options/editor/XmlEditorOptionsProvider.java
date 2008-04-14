package com.intellij.application.options.editor;

import com.intellij.openapi.options.ConfigurationException;

import javax.swing.*;

/**
 * @author spleaner
 */
public class XmlEditorOptionsProvider implements EditorOptionsProvider {
  private JCheckBox myShowBreadcrumbsCheckBox;
  private JPanel myWholePanel;

  public String getDisplayName() {
    return "XML";
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
    final XmlEditorOptions xmlEditorOptions = XmlEditorOptions.getInstance();
    return xmlEditorOptions.isBreadcrumbsEnabled() != myShowBreadcrumbsCheckBox.isSelected();
  }

  public void apply() throws ConfigurationException {
    final XmlEditorOptions xmlEditorOptions = XmlEditorOptions.getInstance();
    xmlEditorOptions.setBreadcrumbsEnabled(myShowBreadcrumbsCheckBox.isSelected());
  }

  public void reset() {
    final XmlEditorOptions xmlEditorOptions = XmlEditorOptions.getInstance();
    myShowBreadcrumbsCheckBox.setSelected(xmlEditorOptions.isBreadcrumbsEnabled());
  }

  public void disposeUIResources() {
  }
}
