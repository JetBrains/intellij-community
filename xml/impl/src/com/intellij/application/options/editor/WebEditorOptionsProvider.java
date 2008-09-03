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
  private JCheckBox myAutomaticallyInsertClosingTagCheckBox;

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
    return xmlEditorOptions.isBreadcrumbsEnabled() != myShowBreadcrumbsCheckBox.isSelected() || xmlEditorOptions.isShowCssColorPreviewInGutter() != myShowCSSColorPreviewCheckBox.isSelected()
        || xmlEditorOptions.isAutomaticallyInsertClosingTag() != myAutomaticallyInsertClosingTagCheckBox.isSelected();
  }

  public void apply() throws ConfigurationException {
    final WebEditorOptions xmlEditorOptions = WebEditorOptions.getInstance();
    xmlEditorOptions.setBreadcrumbsEnabled(myShowBreadcrumbsCheckBox.isSelected());
    xmlEditorOptions.setShowCssColorPreviewInGutter(myShowCSSColorPreviewCheckBox.isSelected());
    xmlEditorOptions.setAutomaticallyInsertClosingTag(myAutomaticallyInsertClosingTagCheckBox.isSelected());
  }

  public void reset() {
    final WebEditorOptions xmlEditorOptions = WebEditorOptions.getInstance();
    myShowBreadcrumbsCheckBox.setSelected(xmlEditorOptions.isBreadcrumbsEnabled());
    myShowCSSColorPreviewCheckBox.setSelected(xmlEditorOptions.isShowCssColorPreviewInGutter());
    myAutomaticallyInsertClosingTagCheckBox.setSelected(xmlEditorOptions.isAutomaticallyInsertClosingTag());
  }

  public void disposeUIResources() {
  }
}
