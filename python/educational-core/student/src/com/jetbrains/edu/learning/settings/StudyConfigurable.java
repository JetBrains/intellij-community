/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.jetbrains.edu.learning.settings;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.jetbrains.edu.learning.StudyPluginConfigurator;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;

public class StudyConfigurable implements SearchableConfigurable {
  public static final String ID = "com.jetbrains.edu.learning.stepic.EduConfigurable";
  private JPanel myMainPanel;
  private ArrayList<ModifiableSettingsPanel> myPluginsSettingsPanels;
  private StudyBaseSettingsPanel mySettingsPanel;

  public StudyConfigurable() {
  }

  @NotNull
  @Override
  public String getId() {
    return ID;
  }

  @Nullable
  @Override
  public Runnable enableSearch(String option) {
    return null;
  }

  @Nls
  @Override
  public String getDisplayName() {
    return "Educational";
  }

  @NotNull
  @Override
  public String getHelpTopic() {
    return ID;
  }

  @Nullable
  @Override
  public JComponent createComponent() {
    if (myMainPanel == null) {
      myMainPanel = new JPanel(new VerticalFlowLayout());
    }
    mySettingsPanel = new StudyBaseSettingsPanel();
    myMainPanel.add(mySettingsPanel.getPanel());

    myPluginsSettingsPanels = new ArrayList<>();
    StudyPluginConfigurator[] extensions = StudyPluginConfigurator.EP_NAME.getExtensions();
    for (StudyPluginConfigurator configurator: extensions) {
      final ModifiableSettingsPanel settingsPanel = configurator.getSettingsPanel();
      if (settingsPanel != null) {
        myPluginsSettingsPanels.add(settingsPanel);
        myMainPanel.add(settingsPanel.getPanel());
      }
    }
    return myMainPanel;
  }

  @Override
  public boolean isModified() {
    if (mySettingsPanel != null) {
      boolean isModified = mySettingsPanel.isModified();
      if (myPluginsSettingsPanels != null && !myPluginsSettingsPanels.isEmpty()) {
        for (ModifiableSettingsPanel settingsPanel: myPluginsSettingsPanels) {
          isModified &= settingsPanel.isModified();
        }
      }
      return isModified;
    }
    return false;
  }

  public void apply() throws ConfigurationException {
    if (myMainPanel != null) {
      mySettingsPanel.apply();
      if (myPluginsSettingsPanels != null && !myPluginsSettingsPanels.isEmpty()) {
        for (ModifiableSettingsPanel settingsPanel: myPluginsSettingsPanels) {
          settingsPanel.apply();
        }
      }
    }
  }

  public void reset() {
    if (myMainPanel != null) {
      mySettingsPanel.apply();
      if (myPluginsSettingsPanels != null && !myPluginsSettingsPanels.isEmpty()) {
        for (ModifiableSettingsPanel settingsPanel: myPluginsSettingsPanels) {
          settingsPanel.reset();
        }
      }
    }
  }

  @Override
  public void disposeUIResources() {
    myMainPanel = null;
  }
}

