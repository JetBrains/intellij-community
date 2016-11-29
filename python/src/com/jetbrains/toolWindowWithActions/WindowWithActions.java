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
import com.intellij.util.ArrayUtil;
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
 * To display console consider using {@link #showConsole(ConsoleView, JComponent, String, Project, Runnable, AnAction...)}.
 * For everything else use {@link #show(JComponent, JComponent, String, Project, Runnable, AnAction...)}
 *
 * @author Ilya.Kazakevich
 */
public final class WindowWithActions {

  private WindowWithActions() {
  }


  /**
   * Displays console with toolwindow with "close" and "stop" actions.
   *
   * @param consoleWithProcess             console to display
   * @param actionListenerComponent component to bind to actions shortcuts (can be null)
   * @param title                   window title (should be unique!)
   * @param project                 project where displaying takes place
   * @param closeListeners          engines to listen for "close" events. Something that stops console process may be good example of it.
   *                                Null if you do not want this delegate to be called.
   * @param customActions           additional actions to add
   */
  public static void showConsoleWithProcess(@NotNull final ConsoleWithProcess consoleWithProcess,
                                            @Nullable final JComponent actionListenerComponent,
                                            @NotNull final String title,
                                            @NotNull final Project project,
                                            @Nullable final Collection<Runnable> closeListeners,
                                            @NotNull final AnAction... customActions) {
    final ConsoleStopProcessAction stopProcessAction = new ConsoleStopProcessAction(consoleWithProcess);

    // Add "stop action" as action and as close listener to stop process when console is closing
    final Collection<Runnable> resultCloseListeners = new ArrayList<>(Collections.singleton(stopProcessAction));
    if (closeListeners != null) {
      resultCloseListeners.addAll(closeListeners);
    }
    final AnAction[] resultActions = ArrayUtil.mergeArrays(new AnAction[]{stopProcessAction}, customActions);
    showConsole(consoleWithProcess, actionListenerComponent, title, project, resultCloseListeners, resultActions);
  }

  /**
   * Displays console in the toolwindow
   *
   * @param consoleView             console to display
   * @param actionListenerComponent component to bind to actions shortcuts (can be null)
   * @param title                   window title (should be unique!)
   * @param project                 project where displaying takes place
   * @param closeListeners          engine to listen for "close" events. Something that stops console process may be good example of it.
   *                                Null if you do not want this delegate to be called.
   * @param customActions           additional actions to add
   */
  public static void showConsole(@NotNull final ConsoleView consoleView,
                                 @Nullable final JComponent actionListenerComponent,
                                 @NotNull final String title,
                                 @NotNull final Project project,
                                 @Nullable final Collection<Runnable> closeListeners,
                                 @NotNull final AnAction... customActions) {
    final AnAction[] actions = ArrayUtil.mergeArrays(customActions, consoleView.createConsoleActions());
    show(consoleView.getComponent(), actionListenerComponent, title, project, closeListeners, actions);
  }

  /**
   * Displays some component in the toolwindow
   *
   * @param dataComponent           component to display
   * @param actionListenerComponent component to bind to actions shortcuts (can be null)
   * @param title                   window title (should be unique!)
   * @param project                 project where displaying takes place
   * @param closeListeners          engines to listen for "close" events. Something that stops console process may be good example of it.
   *                                Null if you do not want this delegate to be called.
   * @param customActions           additional actions to add
   */
  public static void show(@NotNull final JComponent dataComponent,
                          @Nullable final JComponent actionListenerComponent,
                          @NotNull final String title,
                          @NotNull final Project project,
                          @Nullable final Collection<Runnable> closeListeners,
                          @NotNull final AnAction... customActions) {
    final ToolWindowApi api = new ToolWindowApi(project, title);

    final Collection<Runnable> closeListenersToAdd = new ArrayList<>(Collections.singleton(new MyToolWindowCloser(api)));
    if (closeListeners != null) {
      closeListenersToAdd.addAll(closeListeners);
    }
    api.add(PanelWithActions.wrap(dataComponent, closeListenersToAdd, actionListenerComponent, customActions));
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
