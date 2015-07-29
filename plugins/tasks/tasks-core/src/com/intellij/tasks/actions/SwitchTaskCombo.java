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

package com.intellij.tasks.actions;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ex.ComboBoxAction;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.tasks.LocalTask;
import com.intellij.tasks.TaskManager;
import com.intellij.tasks.config.TaskSettings;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author Dmitry Avdeev
 */
public class SwitchTaskCombo extends ComboBoxAction implements DumbAware {

  public JComponent createCustomComponent(final Presentation presentation) {
    ComboBoxButton button = new ComboBoxButton(presentation) {
      @Override
      protected JBPopup createPopup(Runnable onDispose) {
        return SwitchTaskAction.createPopup(DataManager.getInstance().getDataContext(this), onDispose, false);
      }
    };
    button.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 2));
    return button;
  }

  @NotNull
  @Override
  protected DefaultActionGroup createPopupActionGroup(JComponent button) {
    return new DefaultActionGroup();
  }

  @Override
  public void update(AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    Project project = e.getData(CommonDataKeys.PROJECT);
    ComboBoxButton button = (ComboBoxButton)presentation.getClientProperty(CUSTOM_COMPONENT_PROPERTY);
    if (project == null || project.isDefault() || project.isDisposed() || button == null) {
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
