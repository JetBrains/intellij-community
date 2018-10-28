// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.terminal.arrangement;

import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.terminal.TerminalTabState;

@State(name = "TerminalArrangementManager", storages = {
  @Storage(StoragePathMacros.CACHE_FILE)
})
public class TerminalArrangementManager implements PersistentStateComponent<TerminalArrangementState> {

  private final TerminalWorkingDirectoryManager myWorkingDirectoryManager;
  private ToolWindow myTerminalToolWindow;
  private TerminalArrangementState myState;

  public TerminalArrangementManager() {
    myWorkingDirectoryManager = new TerminalWorkingDirectoryManager();
  }

  public void setToolWindow(@NotNull ToolWindow terminalToolWindow) {
    myTerminalToolWindow = terminalToolWindow;
    if (isAvailable()) {
      myWorkingDirectoryManager.init(terminalToolWindow);
    }
  }

  @Nullable
  @Override
  public TerminalArrangementState getState() {
    if (!isAvailable() || myTerminalToolWindow == null) {
      // do not save state, reuse previously stored state
      return null;
    }
    return calcArrangementState(myTerminalToolWindow);
  }

  @Override
  public void loadState(@NotNull TerminalArrangementState state) {
    if (isAvailable()) {
      myState = state;
    }
  }

  @Nullable
  public TerminalArrangementState getArrangementState() {
    return myState;
  }

  @NotNull
  private TerminalArrangementState calcArrangementState(@NotNull ToolWindow terminalToolWindow) {
    TerminalArrangementState arrangementState = new TerminalArrangementState();
    ContentManager contentManager = terminalToolWindow.getContentManager();
    for (Content content : contentManager.getContents()) {
      TerminalTabState tabState = new TerminalTabState();
      tabState.myTabName = content.getTabName();
      tabState.myWorkingDirectory = myWorkingDirectoryManager.getWorkingDirectory(content);
      arrangementState.myTabStates.add(tabState);
    }
    arrangementState.mySelectedTabIndex = contentManager.getIndexOfContent(contentManager.getSelectedContent());
    return arrangementState;
  }

  @NotNull
  public static TerminalArrangementManager getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, TerminalArrangementManager.class);
  }

  private static boolean isAvailable() {
    return Registry.is("terminal.persistent.tabs");
  }
}
