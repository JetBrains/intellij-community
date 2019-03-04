// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.terminal.arrangement;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.terminal.JBTerminalWidget;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.terminal.TerminalTabState;
import org.jetbrains.plugins.terminal.TerminalView;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Set;

@State(name = "TerminalArrangementManager", storages = {
  @Storage(StoragePathMacros.CACHE_FILE)
})
public class TerminalArrangementManager implements PersistentStateComponent<TerminalArrangementState> {

  private static final Logger LOG = Logger.getInstance(TerminalArrangementManager.class);

  private final TerminalWorkingDirectoryManager myWorkingDirectoryManager;
  private ToolWindow myTerminalToolWindow;
  private TerminalArrangementState myState;
  private final Set<String> myTrackingCommandHistoryFileNames = ContainerUtil.newConcurrentSet();

  public TerminalArrangementManager() {
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
    return calcArrangementState(myTerminalToolWindow);
  }

  @Override
  public void loadState(@NotNull TerminalArrangementState state) {
    if (isAvailable()) {
      myState = state;
      myTrackingCommandHistoryFileNames.addAll(getCommandHistoryFileNames(state));
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
      String historyFilePath = terminalWidget.getCommandHistoryFilePath();
      tabState.myCommandHistoryFileName = historyFilePath != null ? PathUtil.getFileName(historyFilePath) : null;
      arrangementState.myTabStates.add(tabState);
    }
    Content selectedContent = contentManager.getSelectedContent();
    arrangementState.mySelectedTabIndex = selectedContent == null ? -1 : contentManager.getIndexOfContent(selectedContent);
    deleteUnusedCommandHistoryFiles(getCommandHistoryFileNames(arrangementState));
    return arrangementState;
  }

  public void register(@NotNull JBTerminalWidget terminalWidget, @Nullable TerminalTabState tabState) {
    if (!isAvailable()) return;
    File historyDir = getCommandHistoryDirectory();
    if (!FileUtil.createDirectory(historyDir)) {
      LOG.warn("No such directory " + historyDir.getAbsolutePath());
      return;
    }
    File historyFile;
    String historyFileName = tabState != null ? tabState.myCommandHistoryFileName : null;
    if (historyFileName == null) {
      try {
        historyFile = FileUtil.createTempFile(historyDir, "history-", null, true, false);
      }
      catch (IOException e) {
        LOG.error(e);
        return;
      }
    }
    else {
      historyFile = new File(historyDir, historyFileName);
      if (!historyFile.isFile()) {
        try {
          //noinspection ResultOfMethodCallIgnored
          historyFile.createNewFile();
        }
        catch (IOException e) {
          LOG.error(e);
        }
      }
    }
    myTrackingCommandHistoryFileNames.add(historyFile.getName());
    terminalWidget.setCommandHistoryFilePath(historyFile.getAbsolutePath());
  }

  @NotNull
  private static File getCommandHistoryDirectory() {
    return new File(PathManager.getConfigPath() + File.separatorChar + "terminal" + File.separatorChar + "history");
  }

  private void deleteUnusedCommandHistoryFiles(@NotNull List<String> keepCommandHistoryFileNames) {
    myTrackingCommandHistoryFileNames.removeAll(keepCommandHistoryFileNames);
    File historyDir = null;
    for (String fileName : myTrackingCommandHistoryFileNames) {
      if (historyDir == null) {
        historyDir = getCommandHistoryDirectory();
      }
      File file = new File(historyDir, fileName);
      if (file.exists() && !FileUtil.delete(file)) {
        LOG.warn("Cannot delete " + file.getAbsolutePath());
      }
    }
    myTrackingCommandHistoryFileNames.clear();
  }

  @NotNull
  public static TerminalArrangementManager getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, TerminalArrangementManager.class);
  }

  static boolean isAvailable() {
    return Registry.is("terminal.persistent.tabs");
  }
}
