package org.jetbrains.plugins.coursecreator.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.coursecreator.format.TaskWindow;

@SuppressWarnings("ComponentNotRegistered")
public class DeleteTaskWindowAction extends AnAction implements DumbAware {
  @NotNull
  private final TaskWindow myTaskWindow;

  public DeleteTaskWindowAction(@NotNull final TaskWindow taskWindow) {
    super("Delete task window","Delete task window", null);
    myTaskWindow = taskWindow;
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    if (project == null) return;
//    myTaskWindow.remove();
  }

}