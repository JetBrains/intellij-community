package com.intellij.tasks.actions;

import com.intellij.icons.AllIcons;
import com.intellij.ide.util.gotoByName.ChooseByNameBase;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.tasks.Task;
import com.intellij.tasks.TaskManager;
import com.intellij.tasks.doc.TaskPsiElement;
import com.intellij.tasks.impl.TaskUtil;
import com.intellij.ui.LayeredIcon;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.speedSearch.SpeedSearchUtil;
import com.intellij.util.text.Matcher;
import com.intellij.util.text.MatcherHolder;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.UIUtil;
import icons.TasksIcons;

import javax.swing.*;
import java.awt.*;

/**
 * @author Evgeny Zakrevsky
 */
public class TaskCellRenderer extends DefaultListCellRenderer implements MatcherHolder {
  private static final Color REMOTE_TASK_BG_COLOR = new Color(240, 240, 255);
  private Matcher myMatcher;
  private final Project myProject;


  public TaskCellRenderer(Project project) {
    super();
    myProject = project;
  }


  public Component getListCellRendererComponent(JList list, Object value, int index, boolean sel, boolean focus) {
    final JPanel panel = new JPanel(new BorderLayout());
    panel.setBackground(UIUtil.getListBackground(sel));
    panel.setForeground(UIUtil.getListForeground(sel));
    if (value instanceof TaskPsiElement) {
      final Task task = ((TaskPsiElement)value).getTask();
      final SimpleColoredComponent c = new SimpleColoredComponent();
      final boolean isLocalTask = TaskManager.getManager(myProject).findTask(task.getId()) != null;
      final boolean isOld = TaskManager.getManager(myProject).getOpenChangelists(task).isEmpty();

      final Color bg = sel ? UIUtil.getListSelectionBackground() : isLocalTask ? UIUtil.getListBackground() : REMOTE_TASK_BG_COLOR;
      panel.setBackground(bg);
      SimpleTextAttributes attr = getAttributes(sel, task.isClosed());

      c.setIcon(isLocalTask && isOld && task.getIcon() != null ? IconLoader.getTransparentIcon(task.getIcon()) : task.getIcon());
      SpeedSearchUtil.appendColoredFragmentForMatcher(TaskUtil.getTrimmedSummary(task), c, attr, myMatcher, bg, sel);
      panel.add(c, BorderLayout.CENTER);
    }
    else if ("...".equals(value)){
      final SimpleColoredComponent c = new SimpleColoredComponent();
      c.setIcon(EmptyIcon.ICON_16);
      c.append((String)value);
      panel.add(c, BorderLayout.CENTER);
    } else if (GotoTaskAction.CREATE_NEW_TASK_ACTION == value) {
      final SimpleColoredComponent c = new SimpleColoredComponent();
      c.setIcon(LayeredIcon.create(TasksIcons.Unknown, AllIcons.Actions.New));
      c.append(GotoTaskAction.CREATE_NEW_TASK_ACTION.getActionText());
      panel.add(c, BorderLayout.CENTER);
    } else if (ChooseByNameBase.NON_PREFIX_SEPARATOR == value) {
      panel.add(ChooseByNameBase.renderNonPrefixSeparatorComponent(UIUtil.getListBackground()), BorderLayout.CENTER);
    }

    return panel;
  }

  private static SimpleTextAttributes getAttributes(final boolean selected, final boolean taskClosed) {
    return new SimpleTextAttributes(taskClosed ? SimpleTextAttributes.STYLE_STRIKEOUT : SimpleTextAttributes.STYLE_PLAIN,
                                    taskClosed ? UIUtil.getLabelDisabledForeground() : UIUtil.getListForeground(selected));
  }

  @Override
  public void setPatternMatcher(Matcher matcher) {
    myMatcher = matcher;
  }
}
