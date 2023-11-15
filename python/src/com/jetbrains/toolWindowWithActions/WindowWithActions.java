/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.toolWindowWithActions;

import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

/**
 * Creates toolwindow at the bottom of the screen displaying {@link ConsoleView console} or some custom {@link JComponent} with
 * actions on the left side.
 * <br/>
 * To display console consider using {@link #showConsole(ConsoleView, JComponent, String, Project, Collection, AnAction...)}.
 * For everything else use {@link #show(JComponent, JComponent, String, Project, Collection, AnAction...)}
 *
 * @author Ilya.Kazakevich
 */
public final class WindowWithActions {

  private WindowWithActions() {
  }


  /**
   * Displays console with toolwindow with "close" and "stop" actions.
   *
   * @param consoleWithProcess      console to display
   * @param actionListenerComponent component to bind to actions shortcuts (can be null)
   * @param consoleTitle            console title (should be unique!)
   * @param project                 project where displaying takes place
   * @param closeListeners          engines to listen for "close" events. Something that stops console process may be good example of it.
   *                                Null if you do not want this delegate to be called.
   * @param customActions           additional actions to add
   */
  public static void showConsoleWithProcess(@NotNull final ConsoleWithProcess consoleWithProcess,
                                            @Nullable final JComponent actionListenerComponent,
                                            @NotNull @Nls(capitalization = Nls.Capitalization.Title) final String consoleTitle,
                                            @NotNull final Project project,
                                            @NotNull final String toolWindowTitle,
                                            @NotNull final Icon toolWindowIcon,
                                            @Nullable final Collection<? extends Runnable> closeListeners,
                                            final AnAction @NotNull ... customActions) {
    final ConsoleStopProcessAction stopProcessAction = new ConsoleStopProcessAction(consoleWithProcess);

    // Add "stop action" as action and as close listener to stop process when console is closing
    final Collection<Runnable> resultCloseListeners = new ArrayList<>(Collections.singleton(stopProcessAction));
    if (closeListeners != null) {
      resultCloseListeners.addAll(closeListeners);
    }
    final AnAction[] resultActions = ArrayUtil.mergeArrays(new AnAction[]{stopProcessAction}, customActions);

    final ToolWindowApi api = new ToolWindowApi(project, toolWindowTitle, toolWindowIcon);

    api.add(new ConsolePanelWithActions(consoleWithProcess, resultCloseListeners, actionListenerComponent, resultActions), consoleTitle);
  }

  @Nullable
  public static JComponent findWindowByName(@NotNull Project project, @NotNull String toolWindowTitle, @NotNull String windowName) {
    final ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(project);
    ToolWindow window = toolWindowManager.getToolWindow(toolWindowTitle);
    if (window == null) {
      return null;
    }
    Content content = window.getContentManager().findContent(windowName);
    if (content == null) {
      return null;
    }
    return content.getComponent();
  }

  /**
   * Displays some component in the toolwindow
   *
   * @param dataComponent           component to display
   * @param actionListenerComponent component to bind to actions shortcuts (can be null)
   * @param componentWindowTitle    component window title (should be unique!)
   * @param project                 project where displaying takes place
   * @param closeListeners          engines to listen for "close" events. Something that stops console process may be good example of it.
   *                                Null if you do not want this delegate to be called.
   * @param customActions           additional actions to add
   */
  public static void show(@NotNull final JComponent dataComponent,
                          @Nullable final JComponent actionListenerComponent,
                          @NotNull @Nls(capitalization = Nls.Capitalization.Title) final String componentWindowTitle,
                          @NotNull final String toolWindowTitle,
                          @NotNull final Icon toolWindowIcon,
                          @NotNull final Project project,
                          @Nullable final Collection<? extends Runnable> closeListeners,
                          final AnAction @NotNull ... customActions) {
    final ToolWindowApi api = new ToolWindowApi(project, toolWindowTitle, toolWindowIcon);

    final Collection<Runnable> closeListenersToAdd = new ArrayList<>(Collections.singleton(new MyToolWindowCloser(api)));
    if (closeListeners != null) {
      closeListenersToAdd.addAll(closeListeners);
    }
    api.add(new PanelWithActions(dataComponent, closeListenersToAdd, actionListenerComponent, customActions), componentWindowTitle);
  }

  /**
   * When user clicks "close", we should close whole window, so we delegate it to {@link ToolWindowApi}
   */
  private static final class MyToolWindowCloser implements Runnable {
    @NotNull
    private final ToolWindowApi myApi;

    private MyToolWindowCloser(@NotNull final ToolWindowApi api) {
      myApi = api;
    }

    @Override
    public void run() {
      myApi.close();
    }
  }
}
