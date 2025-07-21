// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.arrangement;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.terminal.JBTerminalWidget;
import com.intellij.terminal.ui.TerminalWidget;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.content.ContentManagerEvent;
import com.intellij.ui.content.ContentManagerListener;
import com.intellij.util.Alarm;
import com.intellij.util.TimeoutUtil;
import com.jediterm.terminal.ProcessTtyConnector;
import com.jediterm.terminal.TtyConnector;
import kotlinx.coroutines.CompletableDeferred;
import kotlinx.coroutines.future.FutureKt;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.terminal.ShellTerminalWidget;
import org.jetbrains.plugins.terminal.TerminalToolWindowManager;

import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

public final class TerminalWorkingDirectoryManager {
  private static final Logger LOG = Logger.getInstance(TerminalWorkingDirectoryManager.class);
  private static final int MERGE_WAIT_MILLIS = 500;
  private static final int FETCH_WAIT_MILLIS = 2000;
  private static final Key<String> INITIAL_CWD_KEY = Key.create("initial cwd");

  private final Map<Content, Data> myDataByContentMap = new HashMap<>();

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
    contentManager.addContentManagerListener(new ContentManagerListener() {
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
    JBTerminalWidget widget = TerminalToolWindowManager.getWidgetByContent(content);
    if (widget != null) {
      widget.getTerminalPanel().addCustomKeyListener(listener);
      Disposer.register(content, () -> widget.getTerminalPanel().removeCustomKeyListener(listener));
    }
    myDataByContentMap.put(content, data);
  }

  private static void updateWorkingDirectory(@NotNull Content content, @NotNull Data data) {
    JBTerminalWidget widget = TerminalToolWindowManager.getWidgetByContent(content);
    TerminalWidget newWidget = widget != null ? widget.asNewWidget() : null;
    if (widget != null) {
      data.myWorkingDirectory = getWorkingDirectory(newWidget);
    }
  }

  public static @Nullable String getWorkingDirectory(@NotNull TerminalWidget widget) {
    TtyConnector connector = widget.getTtyConnector();
    if (connector == null) return null;
    return getWorkingDirectory(connector);
  }

  @ApiStatus.Internal
  public static @Nullable String getWorkingDirectory(@NotNull TtyConnector connector) {
    ProcessTtyConnector processConnector = ShellTerminalWidget.getProcessTtyConnector(connector);
    if (processConnector == null) return null;
    try {
      long startNano = System.nanoTime();
      CompletableDeferred<String> cwdDeferred =
        ProcessInfoUtil.getInstance().getCurrentWorkingDirectoryDeferred(processConnector.getProcess());
      CompletableFuture<String> cwdFuture = FutureKt.asCompletableFuture(cwdDeferred);
      String result = cwdFuture.get(FETCH_WAIT_MILLIS, TimeUnit.MILLISECONDS);
      boolean exists = checkDirectory(result);
      if (LOG.isDebugEnabled()) {
        LOG.debug("Cwd (" + result + ", exists=" + exists + ") fetched in " + TimeoutUtil.getDurationMillis(startNano) + " ms");
      }
      return exists ? result : null;
    }
    catch (InterruptedException ignored) {
    }
    catch (ExecutionException e) {
      String message = "Failed to fetch cwd for " + connector;
      if (LOG.isDebugEnabled()) {
        LOG.warn(message, e);
      }
      else {
        LOG.warn(message + ": " + e.getCause().getMessage());
      }
    }
    catch (TimeoutException e) {
      LOG.warn("Timeout fetching cwd for " + connector, e);
    }
    return null;
  }

  /**
   * @deprecated use {@link #getWorkingDirectory(TerminalWidget)} instead
   */
  @Deprecated(forRemoval = true)
  public static @Nullable String getWorkingDirectory(@NotNull JBTerminalWidget widget, @Nullable String name) {
    return getWorkingDirectory(widget.asNewWidget());
  }

  private static boolean checkDirectory(@Nullable String directory) {
    if (directory == null) return false;
    try {
      Path path = Path.of(directory);
      return path.isAbsolute() && Files.isDirectory(path);
    }
    catch (InvalidPathException e) {
      return false;
    }
  }

  private void unwatchTab(@NotNull Content content) {
    Data data = getData(content);
    if (data != null) {
      myDataByContentMap.remove(content);
      JBTerminalWidget widget = TerminalToolWindowManager.getWidgetByContent(content);
      if (widget != null) {
        widget.getTerminalPanel().removeCustomKeyListener(data.myKeyListener);
      }
    }
  }

  private @Nullable Data getData(@NotNull Content content) {
    Data data = myDataByContentMap.get(content);
    if (data == null) {
      LOG.error("No associated data");
    }
    return data;
  }

  public static void setInitialWorkingDirectory(@NotNull Content content, @Nullable String directory) {
    content.putUserData(INITIAL_CWD_KEY, directory);
  }

  private static final class Data {
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
