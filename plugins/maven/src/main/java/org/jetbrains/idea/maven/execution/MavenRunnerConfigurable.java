// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.execution;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class MavenRunnerConfigurable extends MavenRunnerPanel implements SearchableConfigurable, Configurable.NoScroll {

  public static final String SETTINGS_ID = "reference.settings.project.maven.runner";

  public MavenRunnerConfigurable(@NotNull Project p, boolean isRunConfiguration) {
    super(p, isRunConfiguration);
  }

  protected abstract @Nullable MavenRunnerSettings getState();

  @Override
  public boolean isModified() {
    MavenRunnerSettings s = new MavenRunnerSettings();
    setData(s);
    return !s.equals(getState());
  }

  @Override
  public void apply() throws ConfigurationException {
    setData(getState());
  }

  @Override
  public void reset() {
    getData(getState());
  }

  @Override
  public @Nls String getDisplayName() {
    return RunnerBundle.message("maven.tab.runner");
  }

  @Override
  public @Nullable @NonNls String getHelpTopic() {
    return SETTINGS_ID;
  }

  @Override
  public @NotNull String getId() {
    //noinspection ConstantConditions
    return getHelpTopic();
  }
}
