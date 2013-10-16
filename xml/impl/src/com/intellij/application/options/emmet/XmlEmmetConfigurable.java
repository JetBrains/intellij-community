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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.UnnamedConfigurable;
import com.intellij.ui.components.JBCheckBox;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * User: zolotov
 * Date: 2/20/13
 */
public class XmlEmmetConfigurable implements UnnamedConfigurable, Disposable, Configurable.NoScroll {
  private JBCheckBox myEnableBEMFilterJBCheckBox;
  private JPanel myPanel;
  private JBCheckBox myEnableEmmetJBCheckBox;
  private JBCheckBox myEnablePreviewJBCheckBox;

  public XmlEmmetConfigurable() {
    myEnableEmmetJBCheckBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        boolean selected = myEnableEmmetJBCheckBox.isSelected();
        myEnableBEMFilterJBCheckBox.setEnabled(selected);
        myEnablePreviewJBCheckBox.setEnabled(selected);
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
           emmetOptions.isBemFilterEnabledByDefault() != myEnableBEMFilterJBCheckBox.isSelected();
  }


  @Override
  public void apply() throws ConfigurationException {
    EmmetOptions emmetOptions = EmmetOptions.getInstance();
    emmetOptions.setEmmetEnabled(myEnableEmmetJBCheckBox.isSelected());
    emmetOptions.setBemFilterEnabledByDefault(myEnableBEMFilterJBCheckBox.isSelected());
    emmetOptions.setPreviewEnabled(myEnablePreviewJBCheckBox.isSelected());
  }

  @Override
  public void reset() {
    EmmetOptions emmetOptions = EmmetOptions.getInstance();
    myEnableEmmetJBCheckBox.setSelected(emmetOptions.isEmmetEnabled());
    myEnableBEMFilterJBCheckBox.setEnabled(emmetOptions.isEmmetEnabled());
    myEnableBEMFilterJBCheckBox.setSelected(emmetOptions.isBemFilterEnabledByDefault());
    myEnablePreviewJBCheckBox.setEnabled(emmetOptions.isEmmetEnabled());
    myEnablePreviewJBCheckBox.setSelected(emmetOptions.isPreviewEnabled());
  }

  @Override
  public void disposeUIResources() {
  }
}
