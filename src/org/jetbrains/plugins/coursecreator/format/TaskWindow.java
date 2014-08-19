package org.jetbrains.plugins.coursecreator.format;

import com.google.gson.annotations.Expose;
import org.jetbrains.annotations.NotNull;

public class TaskWindow {

  @Expose public int line = 0;
  @Expose public int start = 0;
  @Expose public String hint = "";
  @Expose public String possible_answer = "";
  @Expose public int length = 0;
  public String myTaskText = "";

  public TaskWindow() {}

  public TaskWindow(int line, int start, int length) {

  }

  public void setTaskText(@NotNull final String taskText) {
    myTaskText = taskText;
  }

  public String getTaskText() {
    return myTaskText;
  }

  public TaskWindow(int line, int start, @NotNull final String hint, @NotNull final String possible_answer, int length) {
    this.line = line;
    this.start = start;
    this.hint = hint;
    this.possible_answer = possible_answer;
    this.length = length;
  }
}