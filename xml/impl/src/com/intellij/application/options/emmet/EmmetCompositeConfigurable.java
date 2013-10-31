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
import com.intellij.codeInsight.template.emmet.generators.ZenCodingGenerator;
import com.intellij.codeInsight.template.impl.TemplateSettings;
import com.intellij.openapi.options.CompositeConfigurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.options.UnnamedConfigurable;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xml.XmlBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * User: zolotov
 * Date: 9/24/13
 */
public class EmmetCompositeConfigurable extends CompositeConfigurable<UnnamedConfigurable> implements SearchableConfigurable {
  private JPanel myRootPanel;
  private JPanel myGeneratorSettingsPanel;
  private JComboBox myEmmetExpandShortcutCombo;

  private static final String SPACE = CodeInsightBundle.message("template.shortcut.space");
  private static final String TAB = CodeInsightBundle.message("template.shortcut.tab");
  private static final String ENTER = CodeInsightBundle.message("template.shortcut.enter");

  public EmmetCompositeConfigurable() {
    myEmmetExpandShortcutCombo.addItem(SPACE);
    myEmmetExpandShortcutCombo.addItem(TAB);
    myEmmetExpandShortcutCombo.addItem(ENTER);
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
    final List<UnnamedConfigurable> configurables = getConfigurables();
    myGeneratorSettingsPanel.setLayout(new GridLayoutManager(configurables.size(), 1, new Insets(0, 0, 10, 0), -1, -1));
    for (int i = 0; i < configurables.size(); i++) {
      UnnamedConfigurable configurable = configurables.get(i);
      final JComponent component = configurable.createComponent();
      myGeneratorSettingsPanel.add(component,
                                   new GridConstraints(i, 0, 1, 1, 0, GridConstraints.FILL_HORIZONTAL, 
                                                       GridConstraints.SIZEPOLICY_CAN_GROW | GridConstraints.SIZEPOLICY_WANT_GROW | GridConstraints.SIZEPOLICY_CAN_SHRINK,
                                                       GridConstraints.SIZEPOLICY_CAN_GROW | GridConstraints.SIZEPOLICY_CAN_SHRINK, 
                                                       new Dimension(-1, -1),  new Dimension(-1, -1), new Dimension(-1, -1)));
    }
    myGeneratorSettingsPanel.revalidate();
    myRootPanel.revalidate();
    return myRootPanel;
  }

  @Override
  public void reset() {
    final EmmetOptions emmetOptions = EmmetOptions.getInstance();
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
    super.reset();
  }

  @Override
  public void apply() throws ConfigurationException {
    final EmmetOptions emmetOptions = EmmetOptions.getInstance();
    emmetOptions.setEmmetExpandShortcut(getSelectedEmmetExpandShortcut());
    super.apply();
  }

  @Override
  public boolean isModified() {
    return EmmetOptions.getInstance().getEmmetExpandShortcut() != getSelectedEmmetExpandShortcut() || super.isModified();
  }

  @Override
  public void disposeUIResources() {
    myGeneratorSettingsPanel.removeAll();
    super.disposeUIResources();
  }

  @Override
  protected List<UnnamedConfigurable> createConfigurables() {
    List<UnnamedConfigurable> configurables = ContainerUtil.newArrayList();
    for (ZenCodingGenerator zenCodingGenerator : ZenCodingGenerator.getInstances()) {
      ContainerUtil.addIfNotNull(configurables, zenCodingGenerator.createConfigurable());
    }
    return configurables;
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
}
