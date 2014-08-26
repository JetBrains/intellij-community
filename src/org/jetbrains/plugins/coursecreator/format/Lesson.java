package org.jetbrains.plugins.coursecreator.format;

import com.google.gson.annotations.Expose;
import com.intellij.psi.PsiDirectory;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Lesson {
  @Expose public String name;
  @Expose public List<Task> task_list = new ArrayList<Task>();

  public int myIndex;
  public Map<String, Task> myTasksMap = new HashMap<String, Task>();

  public Lesson() {}

  public Lesson(@NotNull final String name) {
    this.name = name;
  }

  public void addTask(@NotNull final Task task, PsiDirectory taskDirectory) {
    myTasksMap.put(taskDirectory.getName(), task);
    task_list.add(task);
  }

  public Task getTask(@NotNull final String name) {
    return myTasksMap.get(name);
  }

  public List<Task> getTasklist() {
    return task_list;
  }

  public void setIndex(int index) {
    myIndex = index;
  }

  public int getIndex() {
    return myIndex;
  }
}
