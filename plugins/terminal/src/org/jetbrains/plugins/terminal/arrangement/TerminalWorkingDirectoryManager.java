// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.terminal.arrangement;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.terminal.JBTerminalWidget;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.content.ContentManagerAdapter;
import com.intellij.ui.content.ContentManagerEvent;
import com.intellij.util.Alarm;
import com.intellij.util.ObjectUtils;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.containers.ContainerUtil;
import com.jediterm.terminal.ProcessTtyConnector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.terminal.TerminalView;

import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

public class TerminalWorkingDirectoryManager {
  private static final Logger LOG = Logger.getInstance(TerminalWorkingDirectoryManager.class);
  private static final int MERGE_WAIT_MILLIS = 500;
  private static final int FETCH_WAIT_MILLIS = 2000;
  private static final Key<String> INITIAL_CWD_KEY = Key.create("initial cwd");

  private final Map<Content, Data> myDataByContentMap = ContainerUtil.newHashMap();

  TerminalWorkingDirectoryManager() {
  }

  @Nullable
  String getWorkingDirectory(@NotNull Content content) {
    Data data = getData(content);
    return data != null ? data.myWorkingDirectory : null;
  }

  void init(@NotNull ToolWindow terminalToolWindow) {
    ContentManager contentManager = terminalToolWindow.getContentManager();
    for (Content content : contentManager.getContents()) {
      watchTab(content);
    }
    contentManager.addContentManagerListener(new ContentManagerAdapter() {
      @Override
      public void contentAdded(@NotNull ContentManagerEvent event) {
        watchTab(event.getContent());
      }

      @Override
      public void contentRemoved(@NotNull ContentManagerEvent event) {
        unwatchTab(event.getContent());
      }
    });
  }

  private void watchTab(@NotNull Content content) {
    Alarm alarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, content);
    AtomicReference<Data> dataRef = new AtomicReference<>();
    KeyAdapter listener = new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_ENTER && TerminalArrangementManager.isAvailable()) {
          alarm.cancelAllRequests();
          if (!alarm.isDisposed()) {
            alarm.addRequest(() -> updateWorkingDirectory(content, dataRef.get()), MERGE_WAIT_MILLIS);
          }
        }
      }
    };
    Data data = new Data(listener, content.getDisplayName());
    data.myWorkingDirectory = content.getUserData(INITIAL_CWD_KEY);
    content.putUserData(INITIAL_CWD_KEY, null);
    dataRef.set(data);
    JBTerminalWidget widget = Objects.requireNonNull(TerminalView.getWidgetByContent(content));
    widget.getTerminalPanel().addCustomKeyListener(listener);
    Disposer.register(content, new Disposable() {
      @Override
      public void dispose() {
        widget.getTerminalPanel().removeCustomKeyListener(listener);
      }
    });
    myDataByContentMap.put(content, data);
  }

  private static void updateWorkingDirectory(@NotNull Content content, @NotNull Data data) {
    JBTerminalWidget widget = TerminalView.getWidgetByContent(content);
    if (widget == null) return;
    ProcessTtyConnector connector = ObjectUtils.tryCast(widget.getTtyConnector(), ProcessTtyConnector.class);
    if (connector == null) return;
    try {
      long startNano = System.nanoTime();
      Future<String> cwd = ProcessInfoUtil.getCurrentWorkingDirectory(connector.getProcess());
      data.myWorkingDirectory = cwd.get(FETCH_WAIT_MILLIS, TimeUnit.MILLISECONDS);
      if (LOG.isDebugEnabled()) {
        LOG.debug("Cwd (" + data.myWorkingDirectory + ") fetched in " + TimeoutUtil.getDurationMillis(startNano) + " ms");
      }
    }
    catch (InterruptedException ignored) {
    }
    catch (ExecutionException e) {
      String message = "Failed to fetch cwd for " + data.myContentName;
      if (LOG.isDebugEnabled()) {
        LOG.warn(message, e);
      }
      else {
        LOG.warn(message + ": " + e.getCause().getMessage());
      }
    }
    catch (TimeoutException e) {
      LOG.warn("Timeout fetching cwd for " + data.myContentName, e);
    }
  }

  private void unwatchTab(@NotNull Content content) {
    Data data = getData(content);
    if (data != null) {
      myDataByContentMap.remove(content);
      JBTerminalWidget widget = TerminalView.getWidgetByContent(content);
      if (widget != null) {
        widget.getTerminalPanel().removeCustomKeyListener(data.myKeyListener);
      }
    }
  }

  @Nullable
  private Data getData(@NotNull Content content) {
    Data data = myDataByContentMap.get(content);
    if (data == null) {
      LOG.error("No associated data");
    }
    return data;
  }

  public static void setInitialWorkingDirectory(@NotNull Content content, @Nullable VirtualFile fileOrDir) {
    VirtualFile dir = fileOrDir != null && !fileOrDir.isDirectory() ? fileOrDir.getParent() : fileOrDir;
    content.putUserData(INITIAL_CWD_KEY, dir != null ? FileUtil.toSystemDependentName(dir.getPath()) : null);
  }

  private static class Data {
    private final KeyListener myKeyListener;
    private final String myContentName;
    private volatile String myWorkingDirectory;

    private Data(@NotNull KeyListener keyListener, @Nullable String contentName) {
      myKeyListener = keyListener;
      myContentName = contentName;
    }

    @Override
    public String toString() {
      return myContentName + ", cwd: " + myWorkingDirectory;
    }
  }
}
