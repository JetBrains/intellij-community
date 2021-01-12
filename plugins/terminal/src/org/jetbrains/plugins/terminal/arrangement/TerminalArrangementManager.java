// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.terminal.arrangement;

import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.terminal.JBTerminalWidget;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.terminal.ShellTerminalWidget;
import org.jetbrains.plugins.terminal.TerminalTabState;
import org.jetbrains.plugins.terminal.TerminalView;

import java.nio.file.Path;
import java.util.List;

@State(name = "TerminalArrangementManager", storages = @Storage(StoragePathMacros.PRODUCT_WORKSPACE_FILE))
public class TerminalArrangementManager implements PersistentStateComponent<TerminalArrangementState> {

  private final TerminalWorkingDirectoryManager myWorkingDirectoryManager;
  private final Project myProject;
  private ToolWindow myTerminalToolWindow;
  private TerminalArrangementState myState;

  public TerminalArrangementManager(@NotNull Project project) {
    myProject = project;
    myWorkingDirectoryManager = new TerminalWorkingDirectoryManager();
  }

  public void setToolWindow(@NotNull ToolWindow terminalToolWindow) {
    myTerminalToolWindow = terminalToolWindow;
    myWorkingDirectoryManager.init(terminalToolWindow);
  }

  @Nullable
  @Override
  public TerminalArrangementState getState() {
    if (!isAvailable() || myTerminalToolWindow == null) {
      // do not save state, reuse previously stored state
      return null;
    }
    TerminalArrangementState state = calcArrangementState(myTerminalToolWindow);
    TerminalCommandHistoryManager.getInstance().retainCommandHistoryFiles(getCommandHistoryFileNames(state), myProject);
    return state;
  }

  @Override
  public void loadState(@NotNull TerminalArrangementState state) {
    if (isAvailable()) {
      myState = state;
    }
  }

  @NotNull
  private static List<String> getCommandHistoryFileNames(@NotNull TerminalArrangementState state) {
    return ContainerUtil.mapNotNull(state.myTabStates, tabState -> tabState.myCommandHistoryFileName);
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
      JBTerminalWidget terminalWidget = TerminalView.getWidgetByContent(content);
      if (terminalWidget == null) continue;
      TerminalTabState tabState = new TerminalTabState();
      tabState.myTabName = content.getTabName();
      tabState.myWorkingDirectory = myWorkingDirectoryManager.getWorkingDirectory(content);
      tabState.myCommandHistoryFileName = TerminalCommandHistoryManager.getFilename(
        ShellTerminalWidget.getCommandHistoryFilePath(terminalWidget)
      );
      arrangementState.myTabStates.add(tabState);
    }
    Content selectedContent = contentManager.getSelectedContent();
    arrangementState.mySelectedTabIndex = selectedContent == null ? -1 : contentManager.getIndexOfContent(selectedContent);
    return arrangementState;
  }

  public void assignCommandHistoryFile(@NotNull JBTerminalWidget terminalWidget, @Nullable TerminalTabState tabState) {
    if (isAvailable() && terminalWidget instanceof ShellTerminalWidget) {
      Path historyFile = TerminalCommandHistoryManager.getInstance().getOrCreateCommandHistoryFile(
        tabState != null ? tabState.myCommandHistoryFileName : null,
        myProject
      );
      String historyFilePath = historyFile != null ? historyFile.toAbsolutePath().toString() : null;
      ((ShellTerminalWidget)terminalWidget).setCommandHistoryFilePath(historyFilePath);
    }
  }

  public static @NotNull TerminalArrangementManager getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, TerminalArrangementManager.class);
  }

  static boolean isAvailable() {
    return Registry.is("terminal.persistent.tabs");
  }
}
