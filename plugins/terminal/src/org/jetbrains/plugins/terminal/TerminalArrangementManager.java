// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.terminal;

import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@State(name = "TerminalArrangementManager", storages = {
  @Storage(StoragePathMacros.CACHE_FILE),
})
public class TerminalArrangementManager implements PersistentStateComponent<TerminalArrangementState> {

  private final Project myProject;
  private TerminalArrangementState myState;

  public TerminalArrangementManager(@NotNull Project project) {
    myProject = project;
  }

  @Nullable
  @Override
  public TerminalArrangementState getState() {
    return TerminalView.getInstance(myProject).getArrangementState();
  }

  @Override
  public void loadState(@NotNull TerminalArrangementState state) {
    myState = state;
  }

  @Nullable
  TerminalArrangementState getArrangementState() {
    return myState;
  }

  @NotNull
  static TerminalArrangementManager getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, TerminalArrangementManager.class);
  }
}
