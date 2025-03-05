// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsManagerImpl;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.options.UnnamedConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public class MavenImportingConfigurable implements SearchableConfigurable {
  public static final String SETTINGS_ID = "reference.settings.project.maven.importing";

  private final MavenImportingSettings myImportingSettings;
  private final MavenImportingSettingsForm mySettingsForm;
  private final List<UnnamedConfigurable> myAdditionalConfigurables;

  private final @NotNull Disposable myDisposable;

  private final Project myProject;

  public MavenImportingConfigurable(@NotNull Project project) {
    myProject = project;
    final MavenProjectsManager mavenProjectsManager = MavenProjectsManager.getInstance(project);
    myImportingSettings = mavenProjectsManager.getImportingSettings();
    myDisposable = Disposer.newDisposable(mavenProjectsManager, "Maven importing configurable disposable");

    myAdditionalConfigurables = new ArrayList<>();
    for (final AdditionalMavenImportingSettings additionalSettings : AdditionalMavenImportingSettings.EP_NAME.getExtensions()) {
      myAdditionalConfigurables.add(additionalSettings.createConfigurable(project));
    }
    mySettingsForm = new MavenImportingSettingsForm(myProject, myDisposable);
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
    Disposer.dispose(myDisposable);
  }

  @Override
  public boolean isModified() {
    for (final UnnamedConfigurable additionalConfigurable : myAdditionalConfigurables) {
      if (additionalConfigurable.isModified()) {
        return true;
      }
    }

    return mySettingsForm.isModified(myImportingSettings);
  }

  @Override
  public void apply() throws ConfigurationException {
    mySettingsForm.getData(myImportingSettings);
    ExternalProjectsManagerImpl.getInstance(myProject).setStoreExternally(true);

    for (final UnnamedConfigurable additionalConfigurable : myAdditionalConfigurables) {
      additionalConfigurable.apply();
    }
  }

  @Override
  public void reset() {
    mySettingsForm.setData(myImportingSettings, myProject);

    for (final UnnamedConfigurable additionalConfigurable : myAdditionalConfigurables) {
      additionalConfigurable.reset();
    }
  }


  @Override
  public @Nls String getDisplayName() {
    return MavenProjectBundle.message("maven.tab.importing");
  }

  @Override
  public @NotNull @NonNls String getHelpTopic() {
    return SETTINGS_ID;
  }

  @Override
  public @NotNull String getId() {
    return getHelpTopic();
  }
}
