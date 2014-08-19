package org.jetbrains.plugins.coursecreator.format;

import com.google.gson.annotations.Expose;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class TaskFile {
  @Expose public List<TaskWindow> task_windows = new ArrayList<TaskWindow>();

  public TaskFile() {}

  public void addTaskWindow(@NotNull final TaskWindow taskWindow) {
    task_windows.add(taskWindow);
  }
}
