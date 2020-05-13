// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.importing;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@State(name = "MavenCustomPomFilePath")
public class MavenPomPathModuleService implements PersistentStateComponent<MavenPomPathModuleService.MavenPomPathState> {
  private MavenPomPathState myState = new MavenPomPathState();

  public static MavenPomPathModuleService getInstance(final Module module) {
    return module.getService(MavenPomPathModuleService.class);
  }

  public String getPomFileUrl() {
    return myState.mavenPomFileUrl;
  }

  public void setPomFileUrl(String pomFileUrl) {
    myState.mavenPomFileUrl = pomFileUrl;
  }

  @Nullable
  @Override
  public MavenPomPathState getState() {
    return myState;
  }

  @Override
  public void loadState(@NotNull MavenPomPathState state) {
    myState = state;
  }

  public static class MavenPomPathState {
    @Nullable
    public String mavenPomFileUrl;
  }
}
