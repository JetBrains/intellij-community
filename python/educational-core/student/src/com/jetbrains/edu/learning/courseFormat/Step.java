package com.jetbrains.edu.learning.courseFormat;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import com.intellij.util.containers.HashMap;

import java.io.Serializable;
import java.util.Map;

public class Step implements Serializable{

  @SerializedName("task_files")
  @Expose
  private Map<String, TaskFile> myTaskFiles = new HashMap<>();
  private String myText = "";

  public Step() {
  }

  public Step(Task task) {
    for (Map.Entry<String, TaskFile> entry : task.getTaskFiles().entrySet()) {
      String name = entry.getKey();
      TaskFile taskFile = new TaskFile();
      taskFile.name = name;
      taskFile.setTask(task);
      myTaskFiles.put(name, taskFile);
    }
  }

  public Map<String, TaskFile> getTaskFiles() {
    return myTaskFiles;
  }

  public void setTaskFiles(Map<String, TaskFile> taskFiles) {
    myTaskFiles = taskFiles;
  }

  public void init(Task task, boolean isRestarted) {
    for (TaskFile file : myTaskFiles.values()) {
      file.initTaskFile(task, isRestarted);
    }
  }

  public String getText() {
    return myText;
  }

  public void setText(String text) {
    myText = text;
  }
}
