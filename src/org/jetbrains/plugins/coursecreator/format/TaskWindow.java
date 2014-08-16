package org.jetbrains.plugins.coursecreator.format;

import org.jetbrains.annotations.NotNull;

public class TaskWindow {

  public int line = 0;
  public int start = 0;
  public String hint = "";
  public String possible_answer = "";
  public int length = 0;

  public TaskWindow() {}

  public TaskWindow(int line, int start, int length) {

  }

  public TaskWindow(int line, int start, @NotNull final String hint, @NotNull final String possible_answer, int length) {
    this.line = line;
    this.start = start;
    this.hint = hint;
    this.possible_answer = possible_answer;
    this.length = length;
  }
}