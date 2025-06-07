// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.utils;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.execution.MavenRunner;
import org.jetbrains.idea.maven.execution.MavenRunnerConfigurable;
import org.jetbrains.idea.maven.execution.MavenRunnerSettings;
import org.jetbrains.idea.maven.indices.MavenRepositoriesConfigurable;
import org.jetbrains.idea.maven.project.*;
import org.jetbrains.idea.maven.wizards.archetype.MavenCatalogsConfigurable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public final class MavenSettings implements SearchableConfigurable.Parent {
  private final Configurable myConfigurable;
  private final List<Configurable> myChildren;

  public MavenSettings(@NotNull Project project) {

    myConfigurable = new MavenGeneralConfigurable(project);

    myChildren = new ArrayList<>();
    myChildren.add(new MavenImportingConfigurable(project));
    myChildren.add(new MavenIgnoredFilesConfigurable(project));

    myChildren.add(new MyMavenRunnerConfigurable(project));

    myChildren.add(new MavenTestRunningConfigurable(project));

    if (!project.isDefault()) {
      myChildren.add(new MavenRepositoriesConfigurable(project));
    }

    myChildren.add(new MavenCatalogsConfigurable(project));
  }

  @Override
  public boolean hasOwnContent() {
    return true;
  }

  @Override
  public JComponent createComponent() {
    return myConfigurable.createComponent();
  }

  @Override
  public boolean isModified() {
    return myConfigurable.isModified();
  }

  @Override
  public void apply() throws ConfigurationException {
    myConfigurable.apply();
  }

  @Override
  public void reset() {
    myConfigurable.reset();
  }

  @Override
  public void disposeUIResources() {
    myConfigurable.disposeUIResources();
  }

  @Override
  public Configurable @NotNull [] getConfigurables() {
    return myChildren.toArray(new Configurable[0]);
  }

  @Override
  public @NotNull String getId() {
    return MavenSettings.class.getSimpleName();
  }

  @Override
  public @Nls String getDisplayName() {
    return MavenProjectBundle.message("configurable.MavenSettings.display.name");
  }

  @Override
  public String getHelpTopic() {
    return myConfigurable.getHelpTopic();
  }

  public static class MyMavenRunnerConfigurable extends MavenRunnerConfigurable {
    public MyMavenRunnerConfigurable(Project project) {
      super(project, false);
    }

    @Override
    protected MavenRunnerSettings getState() {
      return MavenRunner.getInstance(myProject).getState();
    }
  }
}
