// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.execution;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author Sergey Evdokimov
 */
public class MavenRunnerParametersSettingEditor extends SettingsEditor<MavenRunConfiguration> implements MavenSettingsObservable {

  private final MavenRunnerParametersPanel myPanel;

  public MavenRunnerParametersSettingEditor(@NotNull Project project) {
    myPanel = new MavenRunnerParametersPanel(project);
  }

  @Override
  protected void resetEditorFrom(@NotNull MavenRunConfiguration runConfiguration) {
    myPanel.getData(runConfiguration.getRunnerParameters());
  }

  @Override
  protected void applyEditorTo(@NotNull MavenRunConfiguration runConfiguration) throws ConfigurationException {
    String targetName = MavenRunConfiguration.getTargetName(this);
    myPanel.applyTargetEnvironmentConfiguration(targetName);
    myPanel.setData(runConfiguration.getRunnerParameters());
  }

  @NotNull
  @Override
  protected JComponent createEditor() {
    return myPanel.createComponent();
  }

  @Override
  protected void disposeEditor() {
    myPanel.disposeUIResources();
  }

  @Override
  public void registerSettingsWatcher(@NotNull MavenRCSettingsWatcher watcher) {
    getComponent(); // make sure controls are initialized
    myPanel.registerSettingsWatcher(watcher);
  }
}
