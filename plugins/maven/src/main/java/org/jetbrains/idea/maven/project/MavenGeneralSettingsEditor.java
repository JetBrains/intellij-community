/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.project;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.execution.MavenRunConfiguration;

import javax.swing.*;

/**
 * @author Sergey Evdokimov
 */
public class MavenGeneralSettingsEditor extends SettingsEditor<MavenRunConfiguration> {

  private final MavenGeneralPanel myPanel;

  private JCheckBox myUseProjectSettings;

  private final Project myProject;

  public MavenGeneralSettingsEditor(@NotNull Project project) {
    myProject = project;
    myPanel = new MavenGeneralPanel();
  }

  @Override
  protected void resetEditorFrom(MavenRunConfiguration s) {
    myUseProjectSettings.setSelected(s.getGeneralSettings() == null);

    if (s.getGeneralSettings() == null) {
      MavenGeneralSettings settings = MavenProjectsManager.getInstance(myProject).getGeneralSettings();
      myPanel.getData(settings);
    }
    else {
      myPanel.getData(s.getGeneralSettings());
    }
  }

  @Override
  protected void applyEditorTo(MavenRunConfiguration s) throws ConfigurationException {
    if (myUseProjectSettings.isSelected()) {
      s.setGeneralSettings(null);
    }
    else {
      MavenGeneralSettings state = s.getGeneralSettings();
      if (state != null) {
        myPanel.setData(state);
      }
      else {
        MavenGeneralSettings settings = MavenProjectsManager.getInstance(myProject).getGeneralSettings().clone();
        myPanel.setData(settings);
        s.setGeneralSettings(settings);
      }
    }
  }

  @NotNull
  @Override
  protected JComponent createEditor() {
    Pair<JPanel,JCheckBox> pair = MavenDisablePanelCheckbox.createPanel(myPanel.createComponent(), "Use project settings");

    myUseProjectSettings = pair.second;
    return pair.first;
  }

  @Override
  protected void disposeEditor() {
    myPanel.disposeUIResources();
  }

  public Project getProject() {
    return myProject;
  }
}
