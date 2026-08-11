// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.sh.settings;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

@Service(Service.Level.PROJECT)
@State(name = "ShToolsPaths", storages = @Storage(StoragePathMacros.CACHE_FILE))
public final class ShToolsLocalSettings implements PersistentStateComponent<ShToolsLocalSettings.State> {
  private @NotNull State myState = new State();

  public static ShToolsLocalSettings getInstance(@NotNull Project project) {
    return project.getService(ShToolsLocalSettings.class);
  }

  public @NotNull String getShellcheckPath() { return myState.shellcheckPath; }
  public void setShellcheckPath(@NotNull String path) { myState.shellcheckPath = path; }

  public @NotNull String getShfmtPath() { return myState.shfmtPath; }
  public void setShfmtPath(@NotNull String path) { myState.shfmtPath = path; }

  @Override public @NotNull State getState() { return myState; }
  @Override public void loadState(@NotNull State state) { myState = state; }

  public static final class State {
    public @NotNull String shellcheckPath = "";
    public @NotNull String shfmtPath = "";
  }
}
