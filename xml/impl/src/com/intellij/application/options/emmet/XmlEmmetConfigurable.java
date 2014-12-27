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

import com.intellij.codeInsight.template.emmet.filters.ZenCodingFilter;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.UnnamedConfigurable;
import com.intellij.ui.CheckBoxList;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xml.XmlBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Set;

public class XmlEmmetConfigurable implements UnnamedConfigurable, Disposable, Configurable.NoScroll {
  private JPanel myPanel;
  private JBCheckBox myEnableEmmetJBCheckBox;
  private JBCheckBox myEnablePreviewJBCheckBox;
  private CheckBoxList<ZenCodingFilter> myFiltersCheckBoxList;
  private JPanel myFiltersListPanel;

  public XmlEmmetConfigurable() {
    myEnableEmmetJBCheckBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        boolean selected = myEnableEmmetJBCheckBox.isSelected();
        myEnablePreviewJBCheckBox.setEnabled(selected);
        myFiltersCheckBoxList.setEnabled(selected);
      }
    });
    myFiltersListPanel.setBorder(IdeBorderFactory.createTitledBorder(XmlBundle.message("emmet.filters.enabled.by.default"), false));
    myFiltersCheckBoxList.setItems(ZenCodingFilter.getInstances(), new Function<ZenCodingFilter, String>() {
      @Override
      public String fun(ZenCodingFilter filter) {
        return filter.getDisplayName();
      }
    });
  }

  @Override
  public void dispose() {
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
           !emmetOptions.getFiltersEnabledByDefault().equals(enabledFilters());
  }

  @Override
  public void apply() throws ConfigurationException {
    EmmetOptions emmetOptions = EmmetOptions.getInstance();
    emmetOptions.setEmmetEnabled(myEnableEmmetJBCheckBox.isSelected());
    emmetOptions.setPreviewEnabled(myEnablePreviewJBCheckBox.isSelected());
    emmetOptions.setFiltersEnabledByDefault(enabledFilters());
  }


  @Override
  public void reset() {
    EmmetOptions emmetOptions = EmmetOptions.getInstance();
    myEnableEmmetJBCheckBox.setSelected(emmetOptions.isEmmetEnabled());
    myEnablePreviewJBCheckBox.setEnabled(emmetOptions.isEmmetEnabled());
    myEnablePreviewJBCheckBox.setSelected(emmetOptions.isPreviewEnabled());

    Set<String> enabledByDefault = emmetOptions.getFiltersEnabledByDefault();
    for (ZenCodingFilter filter : ZenCodingFilter.getInstances()) {
      myFiltersCheckBoxList.setItemSelected(filter, enabledByDefault.contains(filter.getSuffix()));
    }
  }

  @Override
  public void disposeUIResources() {
  }

  @NotNull
  private Set<String> enabledFilters() {
    Set<String> result = ContainerUtil.newHashSet();
    for (ZenCodingFilter filter : ZenCodingFilter.getInstances()) {
      if (myFiltersCheckBoxList.isItemSelected(filter)) {
        result.add(filter.getSuffix());
      }
    }
    return result;
  }
}
