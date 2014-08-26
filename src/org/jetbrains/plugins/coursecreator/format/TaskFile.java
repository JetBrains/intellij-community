package org.jetbrains.plugins.coursecreator.format;

import com.google.gson.annotations.Expose;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class TaskFile {
  @Expose public List<TaskWindow> task_windows = new ArrayList<TaskWindow>();
  public int myIndex;

  public TaskFile() {}

  public void addTaskWindow(@NotNull final TaskWindow taskWindow, int index) {
    taskWindow.setIndex(index);
    task_windows.add(taskWindow);
  }

  public List<TaskWindow> getTaskWindows() {
    return task_windows;
  }

  public void setIndex(int index) {
    myIndex = index;
  }

  public int getIndex() {
    return myIndex;
  }
}
