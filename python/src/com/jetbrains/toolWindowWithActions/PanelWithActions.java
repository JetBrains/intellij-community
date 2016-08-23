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

import com.intellij.execution.runners.AbstractConsoleRunnerWithHistory;
import com.intellij.ide.actions.CloseAction;
import com.intellij.openapi.actionSystem.*;
import com.jetbrains.python.PyBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Panel with action buttons that wraps come widget adding action buttons.
 * Entry point is {@link #wrap(JComponent, Collection, JComponent, AnAction...)}.
 *
 * @author Ilya.Kazakevich
 */
@SuppressWarnings({"DeserializableClassInSecureContext", "SerializableClassInSecureContext"}) // Who will serialize panel?
final class PanelWithActions extends JPanel {
  private PanelWithActions() {

  }

  /**
   * Wraps component with panel with buttons returning composite component to add somewhere.
   *
   * @param dataComponent           component to wrap with action panel
   * @param closeListeners          Listeners to delegate "close all" command (when user clicks red cross aka "close" button)
   * @param actionListenerComponent component to bind to action shortcuts (null if no shortcuts will be used)
   * @param customActions           additional actions to add
   * @return composite component with console on the right part and buttons on the left part
   */
  @NotNull
  static JComponent wrap(@NotNull final JComponent dataComponent,
                         @NotNull final Collection<Runnable> closeListeners,
                         @Nullable final JComponent actionListenerComponent,
                         @NotNull final AnAction... customActions) {
    final PanelWithActions instance = new PanelWithActions();

    // Box layout: panel goes to the left, console to the right
    final LayoutManager layout = new BoxLayout(instance, BoxLayout.LINE_AXIS);
    instance.setLayout(layout);

    // use actions from console itself


    final List<AnAction> actionList = new ArrayList<>(Arrays.asList(customActions));
    final DefaultActionGroup toolbarActions = new DefaultActionGroup();
    actionList.add(new MyCloseAction(closeListeners));
    toolbarActions.addAll(actionList);

    final ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.EDITOR_TOOLBAR, toolbarActions, false);
    toolbar.setTargetComponent(dataComponent);
    // This method forces toolbar to add action buttons to its panel to make #getComponents() and #getPreferredSize() work correctly
    toolbar.updateActionsImmediately();

    // TODO: Move GUI and alignment out of here.
    final JComponent toolbarComponent = toolbar.getComponent();

    toolbarComponent.setMaximumSize(toolbarComponent.getPreferredSize()); // To make actions panel as small as possible (not 50% of width)
    toolbarComponent.setAlignmentY(0);  // Align actions to the top
    instance.add(toolbarComponent);
    instance.add(dataComponent);
    if (actionListenerComponent != null) {
      AbstractConsoleRunnerWithHistory.registerActionShortcuts(actionList, actionListenerComponent);
    }
    return instance;
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
    public void update(final AnActionEvent e) {
      super.update(e);
      e.getPresentation().setText(PyBundle.message("windowWithActions.closeWindow"));
    }

    @Override
    public void actionPerformed(final AnActionEvent e) {
      super.actionPerformed(e);
      for (final Runnable closeListener : myCloseListeners) {
        closeListener.run();
      }
    }
  }
}
