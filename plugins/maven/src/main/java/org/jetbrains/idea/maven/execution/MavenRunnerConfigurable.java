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

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Sergey Evdokimov
 */
public abstract class MavenRunnerConfigurable extends MavenRunnerPanel implements SearchableConfigurable, Configurable.NoScroll {

  public static final String SETTINGS_ID = "reference.settings.project.maven.runner";

  public MavenRunnerConfigurable(@NotNull Project p, boolean isRunConfiguration) {
    super(p, isRunConfiguration);
  }

  @Nullable
  protected abstract MavenRunnerSettings getState();

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
  @Nls
  public String getDisplayName() {
    return RunnerBundle.message("maven.tab.runner");
  }

  @Override
  @Nullable
  @NonNls
  public String getHelpTopic() {
    return SETTINGS_ID;
  }

  @Override
  @NotNull
  public String getId() {
    //noinspection ConstantConditions
    return getHelpTopic();
  }
}
