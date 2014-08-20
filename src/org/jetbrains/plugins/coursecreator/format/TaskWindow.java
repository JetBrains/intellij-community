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
  public int myReplacementLength;

  public TaskWindow() {}

  public TaskWindow(int line, int start, int length, String selectedText) {
    this.line = line;
    this.start = start;
    myReplacementLength = length;
    this.possible_answer = selectedText;
  }

  public void setTaskText(@NotNull final String taskText) {
    myTaskText = taskText;
    length = myTaskText.length();
  }

  public String getTaskText() {
    return myTaskText;
  }

  public int getReplacementLength() {
    return myReplacementLength;
  }
}