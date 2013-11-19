/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
  private JBCheckBox mySelectWholeSelectorOnDoubleClick;
  private JBCheckBox myAddQuotasForAttributeValue;


  public String getDisplayName() {
    return XmlBundle.message("web.editor.configuration.title");
  }

  public String getHelpTopic() {
    return null;
  }

  public JComponent createComponent() {
    return myWholePanel;
  }

  public boolean isModified() {
    final WebEditorOptions xmlEditorOptions = WebEditorOptions.getInstance();
    return xmlEditorOptions.isAutomaticallyInsertClosingTag() != myAutomaticallyInsertClosingTagCheckBox.isSelected() ||
           xmlEditorOptions.isAutomaticallyInsertRequiredAttributes() != myAutomaticallyInsertRequiredAttributesCheckBox.isSelected() ||
           xmlEditorOptions.isAutomaticallyStartAttribute() != myAutomaticallyStartAttributeAfterCheckBox.isSelected() ||
           xmlEditorOptions.isSelectWholeCssSelectorSuffixOnDoubleClick() != mySelectWholeSelectorOnDoubleClick.isSelected() ||
           xmlEditorOptions.isAutomaticallyInsertRequiredSubTags() != myAutomaticallyInsertRequiredSubTagsCheckBox.isSelected() ||
           xmlEditorOptions.isInsertQuotesForAttributeValue() != myAddQuotasForAttributeValue.isSelected();
  }

  public void apply() throws ConfigurationException {
    final WebEditorOptions xmlEditorOptions = WebEditorOptions.getInstance();
    xmlEditorOptions.setAutomaticallyInsertClosingTag(myAutomaticallyInsertClosingTagCheckBox.isSelected());
    xmlEditorOptions.setAutomaticallyInsertRequiredAttributes(myAutomaticallyInsertRequiredAttributesCheckBox.isSelected());
    xmlEditorOptions.setAutomaticallyInsertRequiredSubTags(myAutomaticallyInsertRequiredSubTagsCheckBox.isSelected());
    xmlEditorOptions.setAutomaticallyStartAttribute(myAutomaticallyStartAttributeAfterCheckBox.isSelected());
    xmlEditorOptions.setSelectWholeCssSelectorSuffixOnDoubleClick(mySelectWholeSelectorOnDoubleClick.isSelected());
    xmlEditorOptions.setInsertQuotesForAttributeValue(myAddQuotasForAttributeValue.isSelected());
  }

  public void reset() {
    final WebEditorOptions xmlEditorOptions = WebEditorOptions.getInstance();
    myAutomaticallyInsertClosingTagCheckBox.setSelected(xmlEditorOptions.isAutomaticallyInsertClosingTag());
    myAutomaticallyInsertRequiredAttributesCheckBox.setSelected(xmlEditorOptions.isAutomaticallyInsertRequiredAttributes());
    myAutomaticallyInsertRequiredSubTagsCheckBox.setSelected(xmlEditorOptions.isAutomaticallyInsertRequiredSubTags());
    myAutomaticallyStartAttributeAfterCheckBox.setSelected(xmlEditorOptions.isAutomaticallyStartAttribute());
    mySelectWholeSelectorOnDoubleClick.setSelected(xmlEditorOptions.isSelectWholeCssSelectorSuffixOnDoubleClick());
    myAddQuotasForAttributeValue.setSelected(xmlEditorOptions.isInsertQuotesForAttributeValue());
  }

  public void disposeUIResources() {
  }

  @NotNull
  public String getId() {
    return "editor.preferences.webOptions";
  }

  public Runnable enableSearch(final String option) {
    return null;
  }
}
