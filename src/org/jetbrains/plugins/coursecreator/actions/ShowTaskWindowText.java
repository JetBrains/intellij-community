package org.jetbrains.plugins.coursecreator.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.coursecreator.format.TaskWindow;

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
    final String taskText = Messages.showMultilineInputDialog(project, "Task window text", "Task Window Text",
        myTaskWindow.getTaskText(), null, null);
    myTaskWindow.setTaskText(StringUtil.notNullize(taskText));
  }

}