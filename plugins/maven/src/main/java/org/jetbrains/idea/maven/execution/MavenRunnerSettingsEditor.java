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
package org.jetbrains.idea.maven.execution;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.project.MavenDisablePanelCheckbox;

import javax.swing.*;

/**
 * @author Sergey Evdokimov
 */
public class MavenRunnerSettingsEditor extends SettingsEditor<MavenRunConfiguration> {

  private final MavenRunnerPanel myPanel;

  private JCheckBox myUseProjectSettings;

  public MavenRunnerSettingsEditor(@NotNull Project project) {
    myPanel = new MavenRunnerPanel(project, true);
  }

  @Override
  protected void resetEditorFrom(MavenRunConfiguration runConfiguration) {
    myUseProjectSettings.setSelected(runConfiguration.getRunnerSettings() == null);

    if (runConfiguration.getRunnerSettings() == null) {
      MavenRunnerSettings settings = MavenRunner.getInstance(myPanel.getProject()).getSettings();
      myPanel.getData(settings);
    }
    else {
      myPanel.getData(runConfiguration.getRunnerSettings());
    }
  }

  @Override
  protected void applyEditorTo(MavenRunConfiguration runConfiguration) throws ConfigurationException {
    if (myUseProjectSettings.isSelected()) {
      runConfiguration.setRunnerSettings(null);
    }
    else {
      if (runConfiguration.getRunnerSettings() != null) {
        myPanel.setData(runConfiguration.getRunnerSettings());
      }
      else {
        MavenRunnerSettings settings = MavenRunner.getInstance(myPanel.getProject()).getSettings().clone();
        myPanel.setData(settings);
        runConfiguration.setRunnerSettings(settings);
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
}
