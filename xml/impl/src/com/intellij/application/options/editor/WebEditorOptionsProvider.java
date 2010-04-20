/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.template.impl.TemplateSettings;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.xml.XmlBundle;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * @author spleaner
 */
public class WebEditorOptionsProvider implements EditorOptionsProvider {
  private JPanel myWholePanel;
  private JCheckBox myAutomaticallyInsertClosingTagCheckBox;
  private JCheckBox myAutomaticallyInsertRequiredAttributesCheckBox;
  private JCheckBox myAutomaticallyStartAttributeAfterCheckBox;
  private JCheckBox myEnableZenCodingCheckBox;
  private JComboBox myZenCodingExpandShortcutCombo;

  private static final String SPACE = CodeInsightBundle.message("template.shortcut.space");
  private static final String TAB = CodeInsightBundle.message("template.shortcut.tab");
  private static final String ENTER = CodeInsightBundle.message("template.shortcut.enter");

  {
    myZenCodingExpandShortcutCombo.addItem(SPACE);
    myZenCodingExpandShortcutCombo.addItem(TAB);
    myZenCodingExpandShortcutCombo.addItem(ENTER);
    myEnableZenCodingCheckBox.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        myZenCodingExpandShortcutCombo.setEnabled(myEnableZenCodingCheckBox.isSelected());
      }
    });
  }

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

  private char getSelectedZenCodingExpandShortcut() {
    Object selectedItem = myZenCodingExpandShortcutCombo.getSelectedItem();
    if (TAB.equals(selectedItem)) {
      return TemplateSettings.TAB_CHAR;
    }
    else if (ENTER.equals(selectedItem)) {
      return TemplateSettings.ENTER_CHAR;
    }
    return TemplateSettings.SPACE_CHAR;
  }

  public boolean isModified() {
    final WebEditorOptions xmlEditorOptions = WebEditorOptions.getInstance();
    return xmlEditorOptions.isAutomaticallyInsertClosingTag() != myAutomaticallyInsertClosingTagCheckBox.isSelected() ||
           xmlEditorOptions.isAutomaticallyInsertRequiredAttributes() != myAutomaticallyInsertRequiredAttributesCheckBox.isSelected() ||
           xmlEditorOptions.isAutomaticallyStartAttribute() != myAutomaticallyStartAttributeAfterCheckBox.isSelected() ||
           xmlEditorOptions.isZenCodingEnabled() != myEnableZenCodingCheckBox.isSelected() ||
           xmlEditorOptions.getZenCodingExpandShortcut() != getSelectedZenCodingExpandShortcut();
  }

  public void apply() throws ConfigurationException {
    final WebEditorOptions xmlEditorOptions = WebEditorOptions.getInstance();
    xmlEditorOptions.setAutomaticallyInsertClosingTag(myAutomaticallyInsertClosingTagCheckBox.isSelected());
    xmlEditorOptions.setAutomaticallyInsertRequiredAttributes(myAutomaticallyInsertRequiredAttributesCheckBox.isSelected());
    xmlEditorOptions.setAutomaticallyStartAttribute(myAutomaticallyStartAttributeAfterCheckBox.isSelected());
    xmlEditorOptions.setEnableZenCoding(myEnableZenCodingCheckBox.isSelected());
    xmlEditorOptions.setZenCodingExpandShortcut(getSelectedZenCodingExpandShortcut());
  }

  public void reset() {
    final WebEditorOptions xmlEditorOptions = WebEditorOptions.getInstance();
    myAutomaticallyInsertClosingTagCheckBox.setSelected(xmlEditorOptions.isAutomaticallyInsertClosingTag());
    myAutomaticallyInsertRequiredAttributesCheckBox.setSelected(xmlEditorOptions.isAutomaticallyInsertRequiredAttributes());
    myAutomaticallyStartAttributeAfterCheckBox.setSelected(xmlEditorOptions.isAutomaticallyStartAttribute());
    myEnableZenCodingCheckBox.setSelected(xmlEditorOptions.isZenCodingEnabled());
    myZenCodingExpandShortcutCombo.setEnabled(xmlEditorOptions.isZenCodingEnabled());

    char shortcut = (char)WebEditorOptions.getInstance().getZenCodingExpandShortcut();
    if (shortcut == TemplateSettings.TAB_CHAR) {
      myZenCodingExpandShortcutCombo.setSelectedItem(TAB);
    }
    else if (shortcut == TemplateSettings.ENTER_CHAR) {
      myZenCodingExpandShortcutCombo.setSelectedItem(ENTER);
    }
    else {
      myZenCodingExpandShortcutCombo.setSelectedItem(SPACE);
    }
  }

  public void disposeUIResources() {
  }

  public String getId() {
    return "editor.preferences.webOptions";
  }

  public Runnable enableSearch(final String option) {
    return null;
  }
}
