package org.jetbrains.plugins.coursecreator.highlighting;

import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import icons.CourseCreatorIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.coursecreator.actions.CCDeleteTaskWindow;
import org.jetbrains.plugins.coursecreator.actions.CCShowTaskWindowText;
import org.jetbrains.plugins.coursecreator.format.TaskWindow;

import javax.swing.*;

public class TaskTextGutter extends LineMarkerInfo.LineMarkerGutterIconRenderer {
  @NotNull
  private final TaskWindow myTaskWindow;

  public TaskTextGutter(@NotNull final TaskWindow taskWindow, LineMarkerInfo lineMarkerInfo) {
    super(lineMarkerInfo);
    myTaskWindow = taskWindow;
  }

  @NotNull
  @Override
  public Icon getIcon() {
    return CourseCreatorIcons.Gutter;
  }

  @Override
  public boolean equals(Object o) {
    return this == o || o instanceof TaskTextGutter
        && myTaskWindow.getTaskText().equals(((TaskTextGutter) o).getTaskWindow().getTaskText());
  }

  @NotNull
  public TaskWindow getTaskWindow() {
    return myTaskWindow;
  }

  @Override
  public int hashCode() {
    return myTaskWindow.hashCode();
  }

  @Nullable
  @Override
  public AnAction getClickAction() {
    return new CCShowTaskWindowText(myTaskWindow);
  }

  @Nullable
  @Override
  public ActionGroup getPopupMenuActions() {
    DefaultActionGroup group = new DefaultActionGroup();
    group.add(new CCDeleteTaskWindow(myTaskWindow));
    return group;
  }
}
