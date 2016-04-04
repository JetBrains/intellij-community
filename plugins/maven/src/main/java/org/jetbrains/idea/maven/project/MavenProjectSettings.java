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
package org.jetbrains.idea.maven.project;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Sergey Evdokimov
 */
@State(name = "MavenProjectSettings", storages = @Storage("mavenProjectSettings.xml"))
public class MavenProjectSettings implements PersistentStateComponent<MavenProjectSettings> {

  private final Project myProject;

  private MavenTestRunningSettings myTestRunningSettings = new MavenTestRunningSettings();

  public MavenProjectSettings() {
    this(null);
  }

  public MavenProjectSettings(Project project) {
    myProject = project;
  }

  public static MavenProjectSettings getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, MavenProjectSettings.class);
  }

  @Nullable
  @Override
  public MavenProjectSettings getState() {
    return this;
  }

  public MavenTestRunningSettings getTestRunningSettings() {
    return myTestRunningSettings;
  }

  public void setTestRunningSettings(MavenTestRunningSettings testRunningSettings) {
    myTestRunningSettings = testRunningSettings;
  }

  @Override
  public void loadState(MavenProjectSettings state) {
    this.myTestRunningSettings = state.myTestRunningSettings;
  }
}
