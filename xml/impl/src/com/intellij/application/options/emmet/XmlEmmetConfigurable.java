/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.codeInsight.template.emmet.filters.ZenCodingFilter;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.xml.XmlBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class XmlEmmetConfigurable implements SearchableConfigurable, Disposable, Configurable.NoScroll {
  private JPanel myPanel;
  private JBCheckBox myEnableEmmetJBCheckBox;
  private JBCheckBox myEnablePreviewJBCheckBox;
  private JPanel myFiltersListPanel;
  private JBCheckBox myEnableHrefAutodetectJBCheckBox;
  private JBCheckBox myAddEditPointAtTheEndOfTemplateJBCheckBox;
  private JBTextField myBemElementSeparatorTextField;
  private JBTextField myBemModifierSeparatorTextField;
  private JBTextField myBemShortElementPrefixTextField;
  private JPanel myBemPanel;

  private Map<String, JBCheckBox> myFilterCheckboxes = ContainerUtil.newHashMap();

  public XmlEmmetConfigurable() {
    myEnableEmmetJBCheckBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        boolean selected = myEnableEmmetJBCheckBox.isSelected();
        myEnablePreviewJBCheckBox.setEnabled(selected);
        myFiltersListPanel.setEnabled(selected);
        myEnableHrefAutodetectJBCheckBox.setEnabled(selected);
        myAddEditPointAtTheEndOfTemplateJBCheckBox.setEnabled(selected);
        UIUtil.setEnabled(myBemPanel, selected, true);
        for (JBCheckBox checkBox : myFilterCheckboxes.values()) {
          checkBox.setEnabled(selected);
        }
      }
    });
    myFiltersListPanel.setBorder(IdeBorderFactory.createTitledBorder(XmlBundle.message("emmet.filters.enabled.by.default")));
    myBemPanel.setBorder(IdeBorderFactory.createTitledBorder(XmlBundle.message("emmet.bem.title")));
    createFiltersCheckboxes();
    
  }

  public void createFiltersCheckboxes() {
    final List<ZenCodingFilter> filters = ZenCodingFilter.getInstances();
    final GridBagLayout layoutManager = new GridBagLayout();
    final GridBagConstraints constraints = new GridBagConstraints(0, 0, 1, 1, 1, 1, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE,
                                                                  JBUI.emptyInsets(), 0, 0);
    myFiltersListPanel.setLayout(layoutManager);
    int added = 0;
    for (ZenCodingFilter filter : filters) {
      if (myFilterCheckboxes.containsKey(filter.getSuffix())) continue;
      final JBCheckBox checkBox = new JBCheckBox(filter.getDisplayName());
      myFilterCheckboxes.put(filter.getSuffix(), checkBox);
      constraints.gridy = added;
      myFiltersListPanel.add(checkBox, constraints);
      added++;
    }
    myFiltersListPanel.revalidate();
  }

  @Override
  public void dispose() {
    myFilterCheckboxes.clear();
    myFilterCheckboxes = null;
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
           emmetOptions.isPreviewEnabled() != myEnablePreviewJBCheckBox.isSelected() ||
           emmetOptions.isHrefAutoDetectEnabled() != myEnableHrefAutodetectJBCheckBox.isSelected() ||
           emmetOptions.isAddEditPointAtTheEndOfTemplate() != myAddEditPointAtTheEndOfTemplateJBCheckBox.isSelected() ||
           !emmetOptions.getFiltersEnabledByDefault().equals(enabledFilters()) ||
           !emmetOptions.getBemElementSeparator().equals(myBemElementSeparatorTextField.getText()) ||
           !emmetOptions.getBemModifierSeparator().equals(myBemModifierSeparatorTextField.getText()) ||
           !emmetOptions.getBemShortElementPrefix().equals(myBemShortElementPrefixTextField.getText());
  }

  @Override
  public void apply() throws ConfigurationException {
    EmmetOptions emmetOptions = EmmetOptions.getInstance();
    emmetOptions.setEmmetEnabled(myEnableEmmetJBCheckBox.isSelected());
    emmetOptions.setPreviewEnabled(myEnablePreviewJBCheckBox.isSelected());
    emmetOptions.setHrefAutoDetectEnabled(myEnableHrefAutodetectJBCheckBox.isSelected());
    emmetOptions.setAddEditPointAtTheEndOfTemplate(myAddEditPointAtTheEndOfTemplateJBCheckBox.isSelected());
    emmetOptions.setFiltersEnabledByDefault(enabledFilters());
    emmetOptions.setBemElementSeparator(myBemElementSeparatorTextField.getText());
    emmetOptions.setBemModifierSeparator(myBemModifierSeparatorTextField.getText());
    emmetOptions.setBemShortElementPrefix(myBemShortElementPrefixTextField.getText());
  }

  @Override
  public void reset() {
    EmmetOptions emmetOptions = EmmetOptions.getInstance();
    myEnableEmmetJBCheckBox.setSelected(emmetOptions.isEmmetEnabled());
    myEnablePreviewJBCheckBox.setEnabled(emmetOptions.isEmmetEnabled());
    myEnablePreviewJBCheckBox.setSelected(emmetOptions.isPreviewEnabled());
    myEnableHrefAutodetectJBCheckBox.setEnabled(emmetOptions.isEmmetEnabled());
    myEnableHrefAutodetectJBCheckBox.setSelected(emmetOptions.isHrefAutoDetectEnabled());
    myAddEditPointAtTheEndOfTemplateJBCheckBox.setEnabled(emmetOptions.isEmmetEnabled());
    myAddEditPointAtTheEndOfTemplateJBCheckBox.setSelected(emmetOptions.isAddEditPointAtTheEndOfTemplate());
    
    myBemElementSeparatorTextField.setText(emmetOptions.getBemElementSeparator());
    myBemModifierSeparatorTextField.setText(emmetOptions.getBemModifierSeparator());
    myBemShortElementPrefixTextField.setText(emmetOptions.getBemShortElementPrefix());

    Set<String> enabledByDefault = emmetOptions.getFiltersEnabledByDefault();
    for (ZenCodingFilter filter : ZenCodingFilter.getInstances()) {
      final String filterSuffix = filter.getSuffix();
      final JBCheckBox checkBox = myFilterCheckboxes.get(filterSuffix);
      if (checkBox != null) {
        checkBox.setEnabled(emmetOptions.isEmmetEnabled());
        checkBox.setSelected(enabledByDefault.contains(filterSuffix));
      }
    }
  }

  @Override
  public void disposeUIResources() {
    Disposer.dispose(this);
  }

  @NotNull
  private Set<String> enabledFilters() {
    Set<String> result = ContainerUtil.newHashSet();
    for (Map.Entry<String, JBCheckBox> checkbox : myFilterCheckboxes.entrySet()) {
      if (checkbox.getValue().isSelected()) {
        result.add(checkbox.getKey());
      }
    }
    return result;
  }

  @Nls
  @Override
  public String getDisplayName() {
    return "HTML";
  }

  @Nullable
  @Override
  public String getHelpTopic() {
    return getId();
  }

  @NotNull
  @Override
  public String getId() {
    return "reference.idesettings.emmet.xml";
  }
}
