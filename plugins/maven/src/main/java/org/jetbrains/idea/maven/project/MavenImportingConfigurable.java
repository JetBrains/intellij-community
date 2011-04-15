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
package org.jetbrains.idea.maven.project;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.options.UnnamedConfigurable;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public class MavenImportingConfigurable implements SearchableConfigurable {
  private final MavenImportingSettings myImportingSettings;
  private final MavenImportingSettingsForm mySettingsForm = new MavenImportingSettingsForm(false);
  private final List<UnnamedConfigurable> myAdditionalConfigurables;

  public MavenImportingConfigurable(Project project, MavenImportingSettings importingSettings) {
    myImportingSettings = importingSettings;

    myAdditionalConfigurables = new ArrayList<UnnamedConfigurable>();
    for (final AdditionalMavenImportingSettings additionalSettings : AdditionalMavenImportingSettings.EP_NAME.getExtensions()) {
      myAdditionalConfigurables.add(additionalSettings.createConfigurable(project));
    }
  }

  public JComponent createComponent() {
    final JPanel panel = mySettingsForm.getAdditionalSettingsPanel();
    panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
    for (final UnnamedConfigurable additionalConfigurable : myAdditionalConfigurables) {
      panel.add(additionalConfigurable.createComponent());
    }
    return mySettingsForm.createComponent();
  }

  public void disposeUIResources() {
    for (final UnnamedConfigurable additionalConfigurable : myAdditionalConfigurables) {
      additionalConfigurable.disposeUIResources();
    }
  }

  public boolean isModified() {
    for (final UnnamedConfigurable additionalConfigurable : myAdditionalConfigurables) {
      if (additionalConfigurable.isModified()) {
        return true;
      }
    }

    return mySettingsForm.isModified(myImportingSettings);
  }

  public void apply() throws ConfigurationException {
    mySettingsForm.getData(myImportingSettings);

    for (final UnnamedConfigurable additionalConfigurable : myAdditionalConfigurables) {
      additionalConfigurable.apply();
    }
  }

  public void reset() {
    mySettingsForm.setData(myImportingSettings);

    for (final UnnamedConfigurable additionalConfigurable : myAdditionalConfigurables) {
      additionalConfigurable.reset();
    }
  }

  @Nls
  public String getDisplayName() {
    return ProjectBundle.message("maven.tab.importing");
  }

  @Nullable
  public Icon getIcon() {
    return null;
  }

  @Nullable
  @NonNls
  public String getHelpTopic() {
    return "reference.settings.project.maven.importing";
  }

  @NotNull
  public String getId() {
    return getHelpTopic();
  }

  public Runnable enableSearch(String option) {
    return null;
  }
}
