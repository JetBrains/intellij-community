// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.terminal.arrangement;

import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.terminal.JBTerminalWidget;
import com.intellij.terminal.ui.TerminalWidget;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.terminal.AbstractTerminalRunner;
import org.jetbrains.plugins.terminal.ShellTerminalWidget;
import org.jetbrains.plugins.terminal.TerminalTabState;
import org.jetbrains.plugins.terminal.TerminalToolWindowManager;

import java.util.List;

@Service(Service.Level.PROJECT)
@State(name = "TerminalArrangementManager", storages = @Storage(StoragePathMacros.PRODUCT_WORKSPACE_FILE))
public final class TerminalArrangementManager implements PersistentStateComponent<TerminalArrangementState> {

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
      AbstractTerminalRunner<?> runner = TerminalToolWindowManager.getRunnerByContent(content);
      if (runner == null || !runner.isTerminalSessionPersistent()) {
        continue;
      }
      TerminalWidget terminalWidget = TerminalToolWindowManager.findWidgetByContent(content);
      if (terminalWidget == null) continue;
      TerminalTabState tabState = new TerminalTabState();
      tabState.myTabName = content.getTabName();
      tabState.myShellCommand = terminalWidget.getShellCommand();
      tabState.myIsUserDefinedTabTitle = tabState.myTabName.equals(terminalWidget.getTerminalTitle().getUserDefinedTitle());
      tabState.myWorkingDirectory = myWorkingDirectoryManager.getWorkingDirectory(content);
      JBTerminalWidget jbTerminalWidget = JBTerminalWidget.asJediTermWidget(terminalWidget);
      ShellTerminalWidget shellTerminalWidget = ObjectUtils.tryCast(jbTerminalWidget, ShellTerminalWidget.class);
      tabState.myCommandHistoryFileName = TerminalCommandHistoryManager.getFilename(
        shellTerminalWidget != null ? shellTerminalWidget.getCommandHistoryFilePath() : null
      );
      arrangementState.myTabStates.add(tabState);
    }
    Content selectedContent = contentManager.getSelectedContent();
    arrangementState.mySelectedTabIndex = selectedContent == null ? -1 : contentManager.getIndexOfContent(selectedContent);
    return arrangementState;
  }

  public static @NotNull TerminalArrangementManager getInstance(@NotNull Project project) {
    return project.getService(TerminalArrangementManager.class);
  }

  static boolean isAvailable() {
    return Registry.is("terminal.persistent.tabs");
  }
}
