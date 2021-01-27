// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.execution;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.project.MavenConfigurableBundle;
import org.jetbrains.idea.maven.project.MavenDisablePanelCheckbox;
import org.jetbrains.idea.maven.project.MavenProjectBundle;

import javax.swing.*;

public class MavenRunnerSettingsEditor extends SettingsEditor<MavenRunConfiguration> implements MavenSettingsObservable {

  private final MavenRunnerPanel myPanel;

  private JCheckBox myUseProjectSettings;

  public MavenRunnerSettingsEditor(@NotNull Project project) {
    myPanel = new MavenRunnerPanel(project, true);
  }

  @Override
  protected void resetEditorFrom(@NotNull MavenRunConfiguration runConfiguration) {
    boolean localTarget = MavenRunConfiguration.getTargetName(this) == null;
    if (localTarget) {
      myUseProjectSettings.setSelected(runConfiguration.getRunnerSettings() == null);
    }
    else {
      myUseProjectSettings.setSelected(false);
    }

    if (runConfiguration.getRunnerSettings() == null) {
      MavenRunnerSettings settings = MavenRunner.getInstance(myPanel.getProject()).getSettings();
      myPanel.getData(settings);
    }
    else {
      myPanel.getData(runConfiguration.getRunnerSettings());
    }
  }

  @Override
  protected void applyEditorTo(@NotNull MavenRunConfiguration runConfiguration) throws ConfigurationException {
    String targetName = MavenRunConfiguration.getTargetName(this);
    boolean localTarget = targetName == null;
    myUseProjectSettings.setEnabled(localTarget);
    if (!localTarget) {
      myUseProjectSettings.setSelected(false);
      myUseProjectSettings.setToolTipText(MavenConfigurableBundle.message("maven.settings.on.targets.runner.use.project.settings.tooltip"));
    } else {
      myUseProjectSettings.setToolTipText(MavenConfigurableBundle.message("maven.settings.runner.use.project.settings.tooltip"));
    }

    if (myUseProjectSettings.isSelected()) {
      runConfiguration.setRunnerSettings(null);
    }
    else {
      MavenRunnerSettings runnerSettings = runConfiguration.getRunnerSettings();
      myPanel.applyTargetEnvironmentConfiguration(targetName);
      if (runnerSettings != null) {
        myPanel.setData(runnerSettings);
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
    Pair<JPanel,JCheckBox> pair = MavenDisablePanelCheckbox.createPanel(myPanel.createComponent(),
                                                                        MavenProjectBundle.message("label.use.project.settings"));

    myUseProjectSettings = pair.second;
    return pair.first;
  }

  @Override
  public void registerSettingsWatcher(@NotNull MavenRCSettingsWatcher watcher) {
    getComponent(); // make sure controls are initialized
    watcher.registerUseProjectSettingsCheckbox(myUseProjectSettings);
    myPanel.registerSettingsWatcher(watcher);
  }
}
