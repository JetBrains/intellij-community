package org.jetbrains.plugins.coursecreator.highlighting;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.util.IconLoader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.coursecreator.actions.ShowTaskWindowText;
import org.jetbrains.plugins.coursecreator.format.TaskWindow;

import javax.swing.*;

public class TaskTextGutter extends GutterIconRenderer {
  @NotNull
  private final TaskWindow myTaskWindow;

  public TaskTextGutter(@NotNull final TaskWindow taskWindow) {
    myTaskWindow = taskWindow;
  }

  @NotNull
  @Override
  public Icon getIcon() {
    return IconLoader.getIcon("/icons/gutter.png");
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
    return new ShowTaskWindowText(myTaskWindow);
  }
}
