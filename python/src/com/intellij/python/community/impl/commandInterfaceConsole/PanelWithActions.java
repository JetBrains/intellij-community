// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.impl.commandInterfaceConsole;

import com.intellij.execution.runners.AbstractConsoleRunnerWithHistory;
import com.intellij.ide.actions.CloseAction;
import com.intellij.openapi.actionSystem.*;
import com.intellij.util.ArrayUtil;
import com.jetbrains.python.PyBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * Panel with action buttons that wraps come widget adding action buttons.
 * Entry point is {@link #wrap(JComponent, Collection, JComponent, AnAction...)}.
 *
 * @author Ilya.Kazakevich
 */
@SuppressWarnings({"SerializableClassInSecureContext"}) // Who will serialize panel?
final class PanelWithActions extends JPanel {

  private final JComponent myDataComponent;

  /**
   * @param dataComponent           component to be added in the action panel
   * @param closeListeners          Listeners to delegate "close all" command (when user clicks red cross aka "close" button)
   * @param actionListenerComponent component to bind to action shortcuts (null if no shortcuts will be used)
   * @param customActions           additional actions to add
   */
  private PanelWithActions(@NotNull final JComponent dataComponent,
                           @NotNull final Collection<Runnable> closeListeners,
                           @Nullable final JComponent actionListenerComponent,
                           final AnAction @NotNull ... customActions) {
    myDataComponent = dataComponent;

    // Box layout: panel goes to the left, console to the right
    final LayoutManager layout = new BoxLayout(this, BoxLayout.LINE_AXIS);
    this.setLayout(layout);

    // use actions from console itself


    final List<AnAction> actionList = new ArrayList<>(Arrays.asList(customActions));
    final DefaultActionGroup toolbarActions = new DefaultActionGroup();
    actionList.add(new MyCloseAction(closeListeners));
    toolbarActions.addAll(actionList);

    final ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.EDITOR_TOOLBAR, toolbarActions, false);
    toolbar.setTargetComponent(dataComponent);

    this.add(toolbar.getComponent());
    this.add(dataComponent);
    if (actionListenerComponent != null) {
      AbstractConsoleRunnerWithHistory.registerActionShortcuts(actionList, actionListenerComponent);
    }
  }

  @Override
  public boolean isFocusable() {
    return myDataComponent.isFocusable();
  }

  @Override
  public void requestFocus() {
    myDataComponent.requestFocus();
  }

  /**
   * Closes console and notifies close listeners when user clicks "close"
   */
  private static final class MyCloseAction extends CloseAction {
    @NotNull
    private final Collection<Runnable> myCloseListeners = new ArrayList<>();

    /**
     * @param closeListeners engines to be called when user clicks "close"
     */
    MyCloseAction(@NotNull final Collection<Runnable> closeListeners) {
      myCloseListeners.addAll(closeListeners);
    }

    @Override
    public void update(@NotNull final AnActionEvent e) {
      super.update(e);
      e.getPresentation().setText(PyBundle.messagePointer("windowWithActions.closeWindow"));
    }

    @Override
    public void actionPerformed(@NotNull final AnActionEvent e) {
      super.actionPerformed(e);
      for (final Runnable closeListener : myCloseListeners) {
        closeListener.run();
      }
    }
  }

  @NotNull
  public static JComponent createConsolePanelWithActions(
    @NotNull final ConsoleWithProcess consoleWithProcess,
    @Nullable final JComponent actionListenerComponent,
    @Nullable final Collection<? extends Runnable> closeListeners,
    final AnAction @NotNull ... customActions
  ) {
    final ConsoleStopProcessAction stopProcessAction = new ConsoleStopProcessAction(consoleWithProcess);

    // Add "stop action" as action and as close listener to stop process when console is closing
    final Collection<Runnable> resultCloseListeners = new ArrayList<>(Collections.singleton(stopProcessAction));
    if (closeListeners != null) {
      resultCloseListeners.addAll(closeListeners);
    }
    final AnAction[] resultActions = ArrayUtil.mergeArrays(new AnAction[]{stopProcessAction}, customActions);
    return new PanelWithActions(consoleWithProcess.getComponent(), resultCloseListeners, actionListenerComponent, resultActions);
  }
}
