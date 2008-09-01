package com.intellij.application.options.editor;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.xml.XmlBundle;

import javax.swing.*;

/**
 * @author spleaner
 */
public class WebEditorOptionsProvider implements EditorOptionsProvider {
  private JCheckBox myShowBreadcrumbsCheckBox;
  private JPanel myWholePanel;
  private JCheckBox myShowCSSColorPreviewCheckBox;

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
    final XmlEditorOptions xmlEditorOptions = XmlEditorOptions.getInstance();
    return xmlEditorOptions.isBreadcrumbsEnabled() != myShowBreadcrumbsCheckBox.isSelected() || xmlEditorOptions.isShowCssColorPreviewInGutter() != myShowCSSColorPreviewCheckBox.isSelected();
  }

  public void apply() throws ConfigurationException {
    final XmlEditorOptions xmlEditorOptions = XmlEditorOptions.getInstance();
    xmlEditorOptions.setBreadcrumbsEnabled(myShowBreadcrumbsCheckBox.isSelected());
    xmlEditorOptions.setShowCssColorPreviewInGutter(myShowCSSColorPreviewCheckBox.isSelected());
  }

  public void reset() {
    final XmlEditorOptions xmlEditorOptions = XmlEditorOptions.getInstance();
    myShowBreadcrumbsCheckBox.setSelected(xmlEditorOptions.isBreadcrumbsEnabled());
    myShowCSSColorPreviewCheckBox.setSelected(xmlEditorOptions.isShowCssColorPreviewInGutter());
  }

  public void disposeUIResources() {
  }
}
