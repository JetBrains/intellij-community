package org.jetbrains.plugins.coursecreator.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.coursecreator.format.TaskWindow;
import org.jetbrains.plugins.coursecreator.ui.CreateTaskWindowDialog;

@SuppressWarnings("ComponentNotRegistered")
public class ShowTaskWindowText extends DumbAwareAction {
  @NotNull
  private final TaskWindow myTaskWindow;

  public ShowTaskWindowText(@NotNull final TaskWindow taskWindow) {
    super("Add task window","Add task window", null);
    myTaskWindow = taskWindow;
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    if (project == null) return;
    //TODO: copy task window and return if modification canceled
    CreateTaskWindowDialog dlg = new CreateTaskWindowDialog(project, myTaskWindow);
    dlg.show();
  }
}