// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.terminal;

import com.intellij.openapi.components.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.terminal.JBTerminalWidget;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@State(name = "TerminalArrangementManager", storages = {
  @Storage(StoragePathMacros.CACHE_FILE)
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
  TerminalArrangementState getArrangementState() {
    return myState;
  }

  @NotNull
  private TerminalArrangementState calcArrangementState(@NotNull ToolWindow terminalToolWindow) {
    TerminalArrangementState arrangementState = new TerminalArrangementState();
    ContentManager contentManager = terminalToolWindow.getContentManager();
    for (Content content : contentManager.getContents()) {
      TerminalTabState tabState = new TerminalTabState();
      tabState.myTabName = content.getTabName();
      JBTerminalWidget widget = TerminalView.getWidgetByContent(content);
      Future<String> directory = getWorkingDirectory(widget);
      try {
        tabState.myWorkingDirectory = directory != null ? directory.get(1000, TimeUnit.MILLISECONDS) : null;
      }
      catch (InterruptedException ignored) {
      }
      catch (ExecutionException e) {
        LOG.warn("No working directory for " + tabState.myTabName, e);
      }
      catch (TimeoutException e) {
        LOG.warn("Timeout fetching working directory for " + tabState.myTabName, e);
      }
      arrangementState.myTabStates.add(tabState);
    }
    arrangementState.mySelectedTabIndex = contentManager.getIndexOfContent(contentManager.getSelectedContent());
    return arrangementState;
  }

  @Nullable
  private static Future<String> getWorkingDirectory(@NotNull JBTerminalWidget widget) {
    TerminalPtyProcessTtyConnector connector = ObjectUtils.tryCast(widget.getTtyConnector(),
                                                                   TerminalPtyProcessTtyConnector.class);
    if (connector == null) return null;
    Process process = connector.getProcess();
    if (process.isAlive()) {
      return ProcessInfoUtil.getWorkingDirectory(process);
    }
    return null;
  }

  @NotNull
  static TerminalArrangementManager getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, TerminalArrangementManager.class);
  }

  private static boolean isAvailable() {
    return Registry.is("terminal.persistant.tabs");
  }
}
