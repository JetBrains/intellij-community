// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project;

import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class MavenGeneralConfigurable extends MavenGeneralPanel implements SearchableConfigurable {

  private final Project myProject;

  public MavenGeneralConfigurable(Project project) {
    myProject = project;
  }

  private MavenGeneralSettings getState() {
    return MavenProjectsManager.getInstance(myProject).getGeneralSettings();
  }

  @Override
  public JComponent createComponent() {
    if (myProject.isDefault()) {
      showCheckBoxWithAdvancedSettings();
    }
    return super.createComponent();
  }

  @Override
  public boolean isModified() {
    MavenGeneralSettings formData = new MavenGeneralSettings();
    setData(formData);
    return !formData.equals(getState());
  }

  @Override
  public void apply() {
    setData(getState());
  }

  @Override
  public void reset() {
    initializeFormData(getState(), myProject);
  }

  @Override
  public @Nullable @NonNls String getHelpTopic() {
    return "reference.settings.dialog.project.maven";
  }

  @Override
  public @NotNull String getId() {
    return getHelpTopic();
  }
}
