// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.application.options.editor;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.xml.XmlBundle;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author spleaner
 */
public class WebEditorOptionsProvider implements EditorOptionsProvider {
  private JPanel myWholePanel;
  private JCheckBox myAutomaticallyInsertClosingTagCheckBox;
  private JCheckBox myAutomaticallyInsertRequiredAttributesCheckBox;
  private JCheckBox myAutomaticallyInsertRequiredSubTagsCheckBox;
  private JCheckBox myAutomaticallyStartAttributeAfterCheckBox;
  private JBCheckBox mySelectWholeCssIdentifierOnDoubleClick;
  private JBCheckBox myAddQuotasForAttributeValue;
  private JBCheckBox myAutoCloseTagCheckBox;
  private JBCheckBox mySyncTagEditing;

  @Override
  public String getDisplayName() {
    return XmlBundle.message("web.editor.configuration.title");
  }

  @Override
  public JComponent createComponent() {
    return myWholePanel;
  }

  @Override
  public boolean isModified() {
    final WebEditorOptions xmlEditorOptions = WebEditorOptions.getInstance();
    return xmlEditorOptions.isAutomaticallyInsertClosingTag() != myAutomaticallyInsertClosingTagCheckBox.isSelected() ||
           xmlEditorOptions.isAutomaticallyInsertRequiredAttributes() != myAutomaticallyInsertRequiredAttributesCheckBox.isSelected() ||
           xmlEditorOptions.isAutomaticallyStartAttribute() != myAutomaticallyStartAttributeAfterCheckBox.isSelected() ||
           xmlEditorOptions.isSelectWholeCssIdentifierOnDoubleClick() != mySelectWholeCssIdentifierOnDoubleClick.isSelected() ||
           xmlEditorOptions.isAutomaticallyInsertRequiredSubTags() != myAutomaticallyInsertRequiredSubTagsCheckBox.isSelected() ||
           xmlEditorOptions.isInsertQuotesForAttributeValue() != myAddQuotasForAttributeValue.isSelected() ||
           xmlEditorOptions.isAutoCloseTag() != myAutoCloseTagCheckBox.isSelected() ||
           xmlEditorOptions.isSyncTagEditing() != mySyncTagEditing.isSelected();
  }

  @Override
  public void apply() throws ConfigurationException {
    final WebEditorOptions xmlEditorOptions = WebEditorOptions.getInstance();
    xmlEditorOptions.setAutomaticallyInsertClosingTag(myAutomaticallyInsertClosingTagCheckBox.isSelected());
    xmlEditorOptions.setAutomaticallyInsertRequiredAttributes(myAutomaticallyInsertRequiredAttributesCheckBox.isSelected());
    xmlEditorOptions.setAutomaticallyInsertRequiredSubTags(myAutomaticallyInsertRequiredSubTagsCheckBox.isSelected());
    xmlEditorOptions.setAutomaticallyStartAttribute(myAutomaticallyStartAttributeAfterCheckBox.isSelected());
    xmlEditorOptions.setSelectWholeCssIdentifierOnDoubleClick(mySelectWholeCssIdentifierOnDoubleClick.isSelected());
    xmlEditorOptions.setInsertQuotesForAttributeValue(myAddQuotasForAttributeValue.isSelected());
    xmlEditorOptions.setAutoCloseTag(myAutoCloseTagCheckBox.isSelected());
    xmlEditorOptions.setSyncTagEditing(mySyncTagEditing.isSelected());
  }

  @Override
  public void reset() {
    final WebEditorOptions xmlEditorOptions = WebEditorOptions.getInstance();
    myAutomaticallyInsertClosingTagCheckBox.setSelected(xmlEditorOptions.isAutomaticallyInsertClosingTag());
    myAutomaticallyInsertRequiredAttributesCheckBox.setSelected(xmlEditorOptions.isAutomaticallyInsertRequiredAttributes());
    myAutomaticallyInsertRequiredSubTagsCheckBox.setSelected(xmlEditorOptions.isAutomaticallyInsertRequiredSubTags());
    myAutomaticallyStartAttributeAfterCheckBox.setSelected(xmlEditorOptions.isAutomaticallyStartAttribute());
    mySelectWholeCssIdentifierOnDoubleClick.setSelected(xmlEditorOptions.isSelectWholeCssIdentifierOnDoubleClick());
    myAddQuotasForAttributeValue.setSelected(xmlEditorOptions.isInsertQuotesForAttributeValue());
    myAutoCloseTagCheckBox.setSelected(xmlEditorOptions.isAutoCloseTag());
    mySyncTagEditing.setSelected(xmlEditorOptions.isSyncTagEditing());
  }

  @Override
  @NotNull
  public String getId() {
    return "editor.preferences.webOptions";
  }
}
