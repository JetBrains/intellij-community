// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
  public void loadState(@NotNull MavenProjectSettings state) {
    this.myTestRunningSettings = state.myTestRunningSettings;
  }
}
