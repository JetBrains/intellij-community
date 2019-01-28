/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsManagerImpl;
import com.intellij.openapi.externalSystem.service.ui.ExternalSystemJdkComboBox;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.options.UnnamedConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.server.MavenServerManager;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class MavenImportingConfigurable implements SearchableConfigurable {
  private final MavenImportingSettings myImportingSettings;
  private final MavenImportingSettingsForm mySettingsForm = new MavenImportingSettingsForm(false, false);
  private final List<UnnamedConfigurable> myAdditionalConfigurables;

  private final Project myProject;

  public MavenImportingConfigurable(@NotNull Project project) {
    myProject = project;
    myImportingSettings = MavenProjectsManager.getInstance(project).getImportingSettings();

    myAdditionalConfigurables = new ArrayList<>();
    for (final AdditionalMavenImportingSettings additionalSettings : AdditionalMavenImportingSettings.EP_NAME.getExtensions()) {
      myAdditionalConfigurables.add(additionalSettings.createConfigurable(project));
    }
  }

  @Override
  public JComponent createComponent() {
    final JPanel panel = mySettingsForm.getAdditionalSettingsPanel();
    panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

    panel.add(Box.createVerticalStrut(5));

    for (final UnnamedConfigurable additionalConfigurable : myAdditionalConfigurables) {
      panel.add(Box.createVerticalStrut(3));
      panel.add(additionalConfigurable.createComponent());
    }
    return mySettingsForm.createComponent();
  }

  @Override
  public void disposeUIResources() {
    for (final UnnamedConfigurable additionalConfigurable : myAdditionalConfigurables) {
      additionalConfigurable.disposeUIResources();
    }
  }

  @Override
  public boolean isModified() {
    for (final UnnamedConfigurable additionalConfigurable : myAdditionalConfigurables) {
      if (additionalConfigurable.isModified()) {
        return true;
      }
    }

    return mySettingsForm.isModified(myImportingSettings, myProject);
  }

  @Override
  public void apply() throws ConfigurationException {
    mySettingsForm.getData(myImportingSettings);
    ExternalProjectsManagerImpl.getInstance(myProject).setStoreExternally(mySettingsForm.isStoreExternally());

    MavenServerManager.getInstance().setMavenEmbedderVMOptions(myImportingSettings.getVmOptionsForImporter());
    String jdk = myImportingSettings.getJdkForImporter();
    if (jdk != null) {
      MavenServerManager.getInstance().setEmbedderJdk(jdk);
    }

    for (final UnnamedConfigurable additionalConfigurable : myAdditionalConfigurables) {
      additionalConfigurable.apply();
    }
  }

  @Override
  public void reset() {
    readGlobalOptions(myImportingSettings);
    mySettingsForm.setData(myImportingSettings, myProject);

    for (final UnnamedConfigurable additionalConfigurable : myAdditionalConfigurables) {
      additionalConfigurable.reset();
    }
  }

  private void readGlobalOptions(MavenImportingSettings settings) {
    // Embedder JDK is an application setting, not a project setting
    settings.setJdkForImporter(MavenServerManager.getInstance().getEmbedderJdk());
    settings.setVmOptionsForImporter(MavenServerManager.getInstance().getMavenEmbedderVMOptions());
  }

  @Override
  @Nls
  public String getDisplayName() {
    return ProjectBundle.message("maven.tab.importing");
  }

  @Override
  @NotNull
  @NonNls
  public String getHelpTopic() {
    return "reference.settings.project.maven.importing";
  }

  @Override
  @NotNull
  public String getId() {
    return getHelpTopic();
  }
}
