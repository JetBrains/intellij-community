package org.jetbrains.plugins.coursecreator.format;

import com.google.gson.annotations.Expose;
import org.jetbrains.annotations.NotNull;

public class TaskWindow {

  @Expose public int line;
  @Expose public int start;
  @Expose public String hint;
  @Expose public String possible_answer;
  @Expose public int length;
  public String myTaskText;

  public TaskWindow() {}

  public TaskWindow(int line, int start, int length) {
    this.line = line;
    this.start = start;
    this.length = length;
  }

  public void setTaskText(@NotNull final String taskText) {
    myTaskText = taskText;
  }

  public String getTaskText() {
    return myTaskText;
  }

}