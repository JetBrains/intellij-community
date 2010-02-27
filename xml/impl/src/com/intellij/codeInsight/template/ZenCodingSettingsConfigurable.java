/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.codeInsight.template;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.xml.XmlBundle;
import org.jetbrains.annotations.Nls;

import javax.swing.*;

/**
 * @author Eugene.Kudelevsky
 */
public class ZenCodingSettingsConfigurable implements SearchableConfigurable {
  private ZenCodingSettingsPanel myPanel;

  public String getId() {
    return getHelpTopic();
  }

  public Runnable enableSearch(String option) {
    return null;
  }

  @Nls
  public String getDisplayName() {
    return XmlBundle.message("zen.coding.settings"); 
  }

  public Icon getIcon() {
    return null;
  }

  public String getHelpTopic() {
    return null;
  }

  public JComponent createComponent() {
    if (myPanel == null) {
      myPanel = new ZenCodingSettingsPanel();
    }
    return myPanel.getComponent();
  }

  public boolean isModified() {
    return myPanel != null && myPanel.isModified();
  }

  public void apply() throws ConfigurationException {
    if (myPanel != null) {
      myPanel.apply();
    }
  }

  public void reset() {
    if (myPanel != null) {
      myPanel.reset();
    }
  }

  public void disposeUIResources() {
    myPanel = null;
  }
}
