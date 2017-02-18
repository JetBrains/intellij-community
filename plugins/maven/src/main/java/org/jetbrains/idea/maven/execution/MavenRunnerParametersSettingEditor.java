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
package org.jetbrains.idea.maven.execution;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author Sergey Evdokimov
 */
public class MavenRunnerParametersSettingEditor extends SettingsEditor<MavenRunConfiguration> {

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
}
