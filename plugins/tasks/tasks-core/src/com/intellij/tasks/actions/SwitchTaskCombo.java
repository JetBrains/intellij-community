/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

package com.intellij.tasks.actions;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ComboBoxAction;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import com.intellij.tasks.LocalTask;
import com.intellij.tasks.TaskManager;
import com.intellij.tasks.config.TaskSettings;
import com.intellij.util.IJSwingUtilities;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author Dmitry Avdeev
 */
public class SwitchTaskCombo extends ComboBoxAction implements DumbAware {

  private static final Key<ComboBoxButton> BUTTON_KEY = Key.create("SWITCH_TASK_BUTTON");

  @Override
  public void actionPerformed(AnActionEvent e) {
    final IdeFrameImpl ideFrame = findFrame(e);
    final ComboBoxButton button = (ComboBoxButton)ideFrame.getRootPane().getClientProperty(BUTTON_KEY);
    if (button == null || !button.isShowing()) return;
    button.showPopup();
  }

  private static IdeFrameImpl findFrame(AnActionEvent e) {
    return IJSwingUtilities.findParentOfType(e.getData(PlatformDataKeys.CONTEXT_COMPONENT), IdeFrameImpl.class);
  }

  public JComponent createCustomComponent(final Presentation presentation) {
    return new ComboBoxButton(presentation) {
      public void addNotify() {
        super.addNotify();
        final IdeFrame frame = UIUtil.getParentOfType(IdeFrame.class, this);
        assert frame != null;
        frame.getComponent().getRootPane().putClientProperty(BUTTON_KEY, this);
      }

      @Override
      protected JBPopup createPopup(Runnable onDispose) {
        return SwitchTaskAction.createPopup(DataManager.getInstance().getDataContext(this), onDispose, false);
      }
    };
  }

  @NotNull
  @Override
  protected DefaultActionGroup createPopupActionGroup(JComponent button) {
    return new DefaultActionGroup();
  }

  @Override
  public void update(AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    Presentation presentation = e.getPresentation();
    if (project == null || project.isDisposed() || (ActionPlaces.MAIN_MENU.equals(e.getPlace()) && findFrame(e) == null)) {
      presentation.setEnabled(false);
      presentation.setText("");
      presentation.setIcon(null);
    }
    else {
      TaskManager taskManager = TaskManager.getManager(project);
      LocalTask activeTask = taskManager.getActiveTask();
      presentation.setVisible(true);
      presentation.setEnabled(true);

      if (isImplicit(activeTask) &&
          taskManager.getAllRepositories().length == 0 &&
          !TaskSettings.getInstance().ALWAYS_DISPLAY_COMBO) {
        presentation.setVisible(false);
      }
      else {
        String s = getText(activeTask);
        presentation.setText(s);
        presentation.setIcon(activeTask.getIcon());
        presentation.setDescription(activeTask.getSummary());
      }
    }
  }

  private static boolean isImplicit(LocalTask activeTask) {
    return activeTask.isDefault() && Comparing.equal(activeTask.getCreated(), activeTask.getUpdated());
  }

  private static String getText(LocalTask activeTask) {
    String text = activeTask.getPresentableName();
    return StringUtil.first(text, 50, true);
  }
}
