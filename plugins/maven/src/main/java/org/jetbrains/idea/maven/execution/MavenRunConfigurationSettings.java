/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.project.MavenGeneralConfigurable;
import org.jetbrains.idea.maven.project.MavenGeneralSettings;

import javax.swing.*;

/**
 * @author Vladislav.Kaznacheev
 */
public class MavenRunConfigurationSettings extends SettingsEditor<MavenRunConfiguration> {
  private MavenRunConfiguration configuration;
  Configurable myCompositeConfigurable;

  public MavenRunConfigurationSettings(final Project p) {
    myCompositeConfigurable = new CompositeConfigurable(
      new MavenRunnerParametersConfigurable() {
        protected MavenRunnerParameters getParameters() {
          return configuration.getRunnerParameters();
        }
      }, new MavenGeneralConfigurable() {
        protected MavenGeneralSettings getState() {
          return configuration.getGeneralSettings();
        }
      }, new MavenRunnerConfigurable(p, true) {
        protected MavenRunnerSettings getState() {
          return configuration.getRunnerSettings();
        }
      });
  }

  protected void resetEditorFrom(MavenRunConfiguration configuration) {
    this.configuration = configuration;
    myCompositeConfigurable.reset();
  }

  protected void applyEditorTo(MavenRunConfiguration configuration) throws ConfigurationException {
    this.configuration = configuration;
    myCompositeConfigurable.apply();
  }

  @NotNull
  protected JComponent createEditor() {
    return myCompositeConfigurable.createComponent();
  }

  protected void disposeEditor() {
    myCompositeConfigurable.disposeUIResources();
  }
}
