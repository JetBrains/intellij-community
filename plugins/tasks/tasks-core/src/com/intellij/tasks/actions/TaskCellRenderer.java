// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.tasks.actions;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.tasks.LocalTask;
import com.intellij.tasks.Task;
import com.intellij.tasks.TaskManager;
import com.intellij.tasks.doc.TaskPsiElement;
import com.intellij.ui.LayeredIcon;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.speedSearch.SpeedSearchUtil;
import com.intellij.util.text.MatcherHolder;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;

/**
 * @author Evgeny Zakrevsky
 */
public class TaskCellRenderer extends DefaultListCellRenderer {
  private final Project myProject;

  public TaskCellRenderer(Project project) {
    super();
    myProject = project;
  }

  @Override
  public Component getListCellRendererComponent(JList list, Object value, int index, boolean sel, boolean focus) {
    final JPanel panel = new JPanel(new BorderLayout());
    panel.setBackground(UIUtil.getListBackground(sel));
    panel.setForeground(UIUtil.getListForeground(sel));
    if (value instanceof TaskPsiElement) {
      final Task task = ((TaskPsiElement)value).getTask();
      final SimpleColoredComponent c = new SimpleColoredComponent();
      final TaskManager taskManager = TaskManager.getManager(myProject);
      final boolean isLocalTask = taskManager.findTask(task.getId()) != null;
      final boolean isClosed = task.isClosed() || (task instanceof LocalTask && taskManager.isLocallyClosed((LocalTask)task));

      final Color bg = sel ? UIUtil.getListSelectionBackground(true) : isLocalTask ? UIUtil.getListBackground() : UIUtil.getDecoratedRowColor();
      panel.setBackground(bg);
      SimpleTextAttributes attr = getAttributes(sel, isClosed);

      c.setIcon(isClosed ? IconLoader.getTransparentIcon(task.getIcon()) : task.getIcon());
      SpeedSearchUtil.appendColoredFragmentForMatcher(task.getPresentableName(), c, attr, MatcherHolder.getAssociatedMatcher(list), bg, sel);
      panel.add(c, BorderLayout.CENTER);
    }
    else if ("...".equals(value)) {
      final SimpleColoredComponent c = new SimpleColoredComponent();
      c.setIcon(EmptyIcon.ICON_16);
      c.append((String)value);
      panel.add(c, BorderLayout.CENTER);
    }
    else if (GotoTaskAction.CREATE_NEW_TASK_ACTION == value) {
      final SimpleColoredComponent c = new SimpleColoredComponent();
      c.setIcon(LayeredIcon.create(AllIcons.FileTypes.Unknown, AllIcons.Actions.New));
      c.append(GotoTaskAction.CREATE_NEW_TASK_ACTION.getActionText());
      panel.add(c, BorderLayout.CENTER);
    }

    return panel;
  }

  private static SimpleTextAttributes getAttributes(final boolean selected, final boolean taskClosed) {
    return new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN,
                                    taskClosed ? UIUtil.getLabelDisabledForeground() : UIUtil.getListForeground(selected));
  }

}
