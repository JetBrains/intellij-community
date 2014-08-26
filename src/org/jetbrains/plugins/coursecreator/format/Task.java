package org.jetbrains.plugins.coursecreator.format;

import com.google.gson.annotations.Expose;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public class Task {
  @Expose public String name;
  @Expose public Map<String, TaskFile> task_files = new HashMap<String, TaskFile>();
  public int myIndex;

  public Task() {}

  public Task(@NotNull final String name) {
    this.name = name;
  }

  public int getIndex() {
    return myIndex;
  }

  public void addTaskFile(@NotNull final String name, int index) {
    TaskFile taskFile = new TaskFile();
    taskFile.setIndex(index);
    task_files.put(name, taskFile);
  }

  public TaskFile getTaskFile(@NotNull final String name) {
    return task_files.get(name);
  }

  public void setIndex(int index) {
    myIndex = index;
  }

  public Map<String, TaskFile> getTaskFiles() {
    return task_files;
  }
}
