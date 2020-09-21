// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.project;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.execution.MavenRunConfiguration;

import javax.swing.*;

public class MavenGeneralSettingsEditor extends SettingsEditor<MavenRunConfiguration> {

  private final MavenGeneralPanel myPanel;

  private JCheckBox myUseProjectSettings;

  private final Project myProject;

  public MavenGeneralSettingsEditor(@NotNull Project project) {
    myProject = project;
    myPanel = new MavenGeneralPanel();
  }

  @Override
  protected void resetEditorFrom(@NotNull MavenRunConfiguration s) {
    boolean localTarget = MavenRunConfiguration.getTargetName(this) == null;
    if (localTarget) {
      myUseProjectSettings.setSelected(s.getGeneralSettings() == null);
    }
    else {
      myUseProjectSettings.setSelected(false);
    }

    if (s.getGeneralSettings() == null) {
      MavenGeneralSettings settings = MavenProjectsManager.getInstance(myProject).getGeneralSettings();
      myPanel.getData(settings);
    }
    else {
      myPanel.getData(s.getGeneralSettings());
    }
  }

  @Override
  protected void applyEditorTo(@NotNull MavenRunConfiguration runConfiguration) throws ConfigurationException {
    String targetName = MavenRunConfiguration.getTargetName(this);
    boolean localTarget = targetName == null;
    myUseProjectSettings.setEnabled(localTarget);
    if (!localTarget) {
      myUseProjectSettings.setSelected(false);
    }

    if (myUseProjectSettings.isSelected()) {
      runConfiguration.setGeneralSettings(null);
    }
    else {
      MavenGeneralSettings generalSettings = runConfiguration.getGeneralSettings();
      myPanel.applyTargetEnvironmentConfiguration(targetName);
      if (generalSettings != null) {
        myPanel.setData(generalSettings);
      }
      else {
        MavenGeneralSettings settings = MavenProjectsManager.getInstance(myProject).getGeneralSettings().clone();
        myPanel.setData(settings);
        runConfiguration.setGeneralSettings(settings);
      }
    }
  }

  @NotNull
  @Override
  protected JComponent createEditor() {
    Pair<JPanel,JCheckBox> pair = MavenDisablePanelCheckbox.createPanel(myPanel.createComponent(),
                                                                        MavenProjectBundle.message("label.use.project.settings"));

    myUseProjectSettings = pair.second;
    return pair.first;
  }

  public Project getProject() {
    return myProject;
  }
}
