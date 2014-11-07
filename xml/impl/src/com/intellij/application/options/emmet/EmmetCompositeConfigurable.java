/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.codeInsight.template.emmet.generators.XmlZenCodingGenerator;
import com.intellij.codeInsight.template.emmet.generators.ZenCodingGenerator;
import com.intellij.codeInsight.template.impl.TemplateExpandShortcutPanel;
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
  private TemplateExpandShortcutPanel myTemplateExpandShortcutPanel;
  
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
      assert component != null;
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
    myTemplateExpandShortcutPanel.setSelectedChar((char)emmetOptions.getEmmetExpandShortcut());
    super.reset();
  }

  @Override
  public void apply() throws ConfigurationException {
    final EmmetOptions emmetOptions = EmmetOptions.getInstance();
    emmetOptions.setEmmetExpandShortcut(myTemplateExpandShortcutPanel.getSelectedChar());
    super.apply();
  }

  @Override
  public boolean isModified() {
    return EmmetOptions.getInstance().getEmmetExpandShortcut() != myTemplateExpandShortcutPanel.getSelectedChar() || super.isModified();
  }

  @Override
  public void disposeUIResources() {
    myGeneratorSettingsPanel.removeAll();
    super.disposeUIResources();
  }

  @Override
  protected List<UnnamedConfigurable> createConfigurables() {
    List<UnnamedConfigurable> xmlConfigurables = ContainerUtil.newSmartList();
    List<UnnamedConfigurable> configurables = ContainerUtil.newSmartList();
    for (ZenCodingGenerator zenCodingGenerator : ZenCodingGenerator.getInstances()) {
      if (zenCodingGenerator instanceof XmlZenCodingGenerator) {
        ContainerUtil.addIfNotNull(xmlConfigurables, zenCodingGenerator.createConfigurable());
      }
      else {
        ContainerUtil.addIfNotNull(configurables, zenCodingGenerator.createConfigurable());
      }
    }
    return ContainerUtil.concat(xmlConfigurables, configurables);
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

  private void createUIComponents() {
    myTemplateExpandShortcutPanel = new TemplateExpandShortcutPanel(XmlBundle.message("emmet.expand.abbreviation.with"));
  }
}
