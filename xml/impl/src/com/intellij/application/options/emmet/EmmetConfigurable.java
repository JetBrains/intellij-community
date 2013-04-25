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
package com.intellij.application.options.emmet;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.template.impl.TemplateSettings;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.xml.XmlBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * User: zolotov
 * Date: 2/20/13
 */
public class EmmetConfigurable implements SearchableConfigurable, Disposable, Configurable.NoScroll {
  private JBCheckBox myEnableEmmetJBCheckBox;
  private JComboBox myEmmetExpandShortcutCombo;
  private JBCheckBox myEnableBEMFilterJBCheckBox;
  private JBCheckBox myAutoInsertCssVendorJBCheckBox;
  private JBCheckBox myEnabledFuzzySearchJBCheckBox;
  private JPanel myPanel;
  private JPanel myPrefixesPanel;

  private CssEditPrefixesListPanel myCssEditPrefixesListPanel;

  private static final String SPACE = CodeInsightBundle.message("template.shortcut.space");
  private static final String TAB = CodeInsightBundle.message("template.shortcut.tab");
  private static final String ENTER = CodeInsightBundle.message("template.shortcut.enter");

  public EmmetConfigurable() {
    myEnableEmmetJBCheckBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        boolean selected = myEnableEmmetJBCheckBox.isSelected();
        myEmmetExpandShortcutCombo.setEnabled(selected);
        //myInsertFallbackGradientColorJBCheckBox.setEnabled(selected);
        myAutoInsertCssVendorJBCheckBox.setEnabled(selected);
        myCssEditPrefixesListPanel.setEnabled(selected && myAutoInsertCssVendorJBCheckBox.isSelected());
        myEnableBEMFilterJBCheckBox.setEnabled(selected);
        myEnabledFuzzySearchJBCheckBox.setEnabled(selected);
      }
    });

    myAutoInsertCssVendorJBCheckBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        myCssEditPrefixesListPanel.setEnabled(myAutoInsertCssVendorJBCheckBox.isSelected());
      }
    });

    myEmmetExpandShortcutCombo.addItem(SPACE);
    myEmmetExpandShortcutCombo.addItem(TAB);
    myEmmetExpandShortcutCombo.addItem(ENTER);
  }

  private char getSelectedEmmetExpandShortcut() {
    Object selectedItem = myEmmetExpandShortcutCombo.getSelectedItem();
    if (TAB.equals(selectedItem)) {
      return TemplateSettings.TAB_CHAR;
    }
    else if (ENTER.equals(selectedItem)) {
      return TemplateSettings.ENTER_CHAR;
    }
    return TemplateSettings.SPACE_CHAR;
  }

  @Override
  public void dispose() {
  }

  @NotNull
  @Override
  public String getId() {
    return "reference.idesettings.emmet";
  }

  @Nullable
  @Override
  public Runnable enableSearch(String option) {
    return null;
  }

  @Nls
  @Override
  public String getDisplayName() {
    return XmlBundle.message("emmet.configuration.title");
  }

  @Nullable
  @Override
  public String getHelpTopic() {
    return getId();
  }

  @Nullable
  @Override
  public JComponent createComponent() {
    return myPanel;
  }

  @Override
  public boolean isModified() {
    EmmetOptions emmetOptions = EmmetOptions.getInstance();
    return emmetOptions.isEmmetEnabled() != myEnableEmmetJBCheckBox.isSelected() ||
           emmetOptions.isBemFilterEnabledByDefault() != myEnableBEMFilterJBCheckBox.isSelected() ||
           emmetOptions.isAutoInsertCssPrefixedEnabled() != myAutoInsertCssVendorJBCheckBox.isSelected() ||
           emmetOptions.isFuzzySearchEnabled() != myEnabledFuzzySearchJBCheckBox.isSelected() ||
           !emmetOptions.getAllPrefixInfo().equals(myCssEditPrefixesListPanel.getState()) ||
           emmetOptions.getEmmetExpandShortcut() != getSelectedEmmetExpandShortcut();
  }


  @Override
  public void apply() throws ConfigurationException {
    EmmetOptions emmetOptions = EmmetOptions.getInstance();
    emmetOptions.setEmmetEnabled(myEnableEmmetJBCheckBox.isSelected());
    emmetOptions.setBemFilterEnabledByDefault(myEnableBEMFilterJBCheckBox.isSelected());
    emmetOptions.setEmmetExpandShortcut(getSelectedEmmetExpandShortcut());
    emmetOptions.setAutoInsertCssPrefixedEnabled(myAutoInsertCssVendorJBCheckBox.isSelected());
    emmetOptions.setFuzzySearchEnabled(myEnabledFuzzySearchJBCheckBox.isSelected());
    emmetOptions.setPrefixInfo(myCssEditPrefixesListPanel.getState());
  }

  @Override
  public void reset() {
    EmmetOptions emmetOptions = EmmetOptions.getInstance();
    myEnableEmmetJBCheckBox.setSelected(emmetOptions.isEmmetEnabled());
    myEnableBEMFilterJBCheckBox.setEnabled(emmetOptions.isEmmetEnabled());
    myEnableBEMFilterJBCheckBox.setSelected(emmetOptions.isBemFilterEnabledByDefault());
    myEmmetExpandShortcutCombo.setEnabled(emmetOptions.isEmmetEnabled());
    myAutoInsertCssVendorJBCheckBox.setEnabled(emmetOptions.isEmmetEnabled());
    myAutoInsertCssVendorJBCheckBox.setSelected(emmetOptions.isAutoInsertCssPrefixedEnabled());
    myEnabledFuzzySearchJBCheckBox.setEnabled(emmetOptions.isEmmetEnabled());
    myEnabledFuzzySearchJBCheckBox.setSelected(emmetOptions.isFuzzySearchEnabled());
    //myInsertFallbackGradientColorJBCheckBox.setEnabled(emmetOptions.isEmmetEnabled());
    //myInsertFallbackGradientColorJBCheckBox.setSelected(emmetOptions.isInsertFallbackGradientColorEnabled());

    myCssEditPrefixesListPanel.setEnabled(emmetOptions.isEmmetEnabled() && emmetOptions.isAutoInsertCssPrefixedEnabled());
    myCssEditPrefixesListPanel.setState(emmetOptions.getAllPrefixInfo());

    char shortcut = (char)emmetOptions.getEmmetExpandShortcut();
    if (shortcut == TemplateSettings.TAB_CHAR) {
      myEmmetExpandShortcutCombo.setSelectedItem(TAB);
    }
    else if (shortcut == TemplateSettings.ENTER_CHAR) {
      myEmmetExpandShortcutCombo.setSelectedItem(ENTER);
    }
    else {
      myEmmetExpandShortcutCombo.setSelectedItem(SPACE);
    }
  }

  @Override
  public void disposeUIResources() {
    myCssEditPrefixesListPanel = null;
    myPrefixesPanel = null;
  }

  private void createUIComponents() {
    myCssEditPrefixesListPanel = new CssEditPrefixesListPanel();
    myPrefixesPanel = myCssEditPrefixesListPanel.createMainComponent();
    myPrefixesPanel.setEnabled(true);
  }
}
