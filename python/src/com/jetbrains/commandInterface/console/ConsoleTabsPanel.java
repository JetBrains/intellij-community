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
package com.jetbrains.commandInterface.console;

import com.intellij.execution.actions.StopProcessAction;
import com.intellij.execution.runners.AbstractConsoleRunnerWithHistory;
import com.intellij.ide.actions.CloseAction;
import com.intellij.openapi.actionSystem.*;
import com.jetbrains.python.PyBundle;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


/**
 * Panel with action buttons that wraps {@link CommandConsole}.
 * Entry point is {@link #wrapConsole(CommandConsole, Runnable)}. You may then just add it to what ever you want.
 *
 * @author Ilya.Kazakevich
 */
@SuppressWarnings({"DeserializableClassInSecureContext", "SerializableClassInSecureContext"}) // Who will serialize panel?
final class ConsoleTabsPanel extends JPanel {
  private ConsoleTabsPanel() {

  }

  /**
   * Wraps console with panel with buttons returning composite component to add somewhere.
   *
   * @param console Console to wrap.
   * @param closer  Listener to delegate "close all" command (when user clicks red cross aka "close" button)
   * @return composite component with console on the right part and buttons on the left part
   */
  @NotNull
  static JComponent wrapConsole(@NotNull final CommandConsole console, @NotNull final Runnable closer) {
    final ConsoleTabsPanel instance = new ConsoleTabsPanel();

    // Box layout: panel goes to the left, console to the right
    final LayoutManager layout = new BoxLayout(instance, BoxLayout.LINE_AXIS);
    instance.setLayout(layout);

    // use actions from console itself
    final List<AnAction> actionList = new ArrayList<AnAction>(Arrays.asList(console.createConsoleActions()));
    actionList.add(new MyCloseAction(closer, console)); // "Close" action
    actionList.add(new MyStopProcessAction(console)); // "Stop process" action


    final DefaultActionGroup toolbarActions = new DefaultActionGroup(actionList);

    final ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.EDITOR_TOOLBAR, toolbarActions, false);
    toolbar.setTargetComponent(console);

    // TODO: Move GUI and alignment out of here.
    final JComponent toolbarComponent = toolbar.getComponent();

    toolbarComponent.setMaximumSize(toolbarComponent.getPreferredSize()); // To make actions panel as small as possible (not 50% of width)
    toolbarComponent.setAlignmentY(0);  // Align actions to the top
    instance.add(toolbarComponent);
    instance.add(console.getComponent());
    AbstractConsoleRunnerWithHistory.registerActionShortcuts(actionList, console.getConsoleEditor().getComponent());
    return instance;
  }

  /**
   * Closes console and notifies closer when user clicks "close"
   */
  private static final class MyCloseAction extends CloseAction {
    @NotNull
    private final Runnable myCloser;
    @NotNull
    private final CommandConsole myConsole;

    /**
     * @param closer engine to be called when user clicks "close"
     * @param console console itself
     */
    MyCloseAction(@NotNull final Runnable closer, @NotNull final CommandConsole console) {
      myCloser = closer;
      myConsole = console;
    }

    @Override
    public void update(final AnActionEvent e) {
      super.update(e);
      e.getPresentation().setText(PyBundle.message("commandLine.closeWindow"));
    }

    @Override
    public void actionPerformed(final AnActionEvent e) {
      super.actionPerformed(e);
      StopProcessAction.stopProcess(myConsole.getProcessHandler()); // Stop process before closing console, no need to left junk
      myCloser.run();
    }
  }

  /**
   * Stops currently running process (if any)
   */
  private static final class MyStopProcessAction extends StopProcessAction {
    private final CommandConsole myConsole;

    /**
     * @param console command console where process takes place
     */
    private MyStopProcessAction(@NotNull final CommandConsole console) {
      super(PyBundle.message("commandLine.stopProcess"), null, null);
      myConsole = console;
    }

    @Override
    public void update(final AnActionEvent e) {
      super.update(e);
      setProcessHandler(myConsole.getProcessHandler()); // Attach action to process handler (if any) or detach (if no process runs)
    }
  }
}
