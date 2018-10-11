// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.terminal;

import com.intellij.openapi.components.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@State(name = "TerminalArrangementManager", storages = {
  @Storage(StoragePathMacros.CACHE_FILE),
})
public class TerminalArrangementManager implements PersistentStateComponent<TerminalArrangementState> {

  private static final Logger LOG = Logger.getInstance(TerminalArrangementManager.class);

  private final Project myProject;
  private ToolWindow myTerminalToolWindow;
  private TerminalArrangementState myState;

  public TerminalArrangementManager(@NotNull Project project) {
    myProject = project;
  }

  void setToolWindow(@NotNull ToolWindow terminalToolWindow) {
    myTerminalToolWindow = terminalToolWindow;
  }

  @Nullable
  @Override
  public TerminalArrangementState getState() {
    return isAvailable() ? calcArrangementState() : null;
  }

  @Override
  public void loadState(@NotNull TerminalArrangementState state) {
    if (isAvailable()) {
      myState = state;
    }
  }

  @Nullable
  TerminalArrangementState getArrangementState() {
    return myState;
  }

  @NotNull
  private TerminalArrangementState calcArrangementState() {
    TerminalArrangementState arrangementState = new TerminalArrangementState();
    if (myTerminalToolWindow == null) {
      LOG.warn("Unavailable " + TerminalToolWindowFactory.TOOL_WINDOW_ID + " toolwindow");
      return arrangementState;
    }
    ContentManager contentManager = myTerminalToolWindow.getContentManager();
    for (Content content : contentManager.getContents()) {
      TerminalTabState tabState = new TerminalTabState();
      tabState.myTabName = content.getTabName();
      arrangementState.myTabStates.add(tabState);
    }
    arrangementState.mySelectedTabIndex = contentManager.getIndexOfContent(contentManager.getSelectedContent());
    return arrangementState;
  }

  @NotNull
  static TerminalArrangementManager getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, TerminalArrangementManager.class);
  }

  private static boolean isAvailable() {
    return Registry.is("terminal.persistant.tabs");
  }
}
