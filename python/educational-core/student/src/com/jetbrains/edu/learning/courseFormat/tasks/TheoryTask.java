package com.jetbrains.edu.learning.courseFormat.tasks;

import org.jetbrains.annotations.NotNull;

public class TheoryTask extends Task {
  @SuppressWarnings("unused") //used for deserialization
  public TheoryTask() {}

  public TheoryTask(@NotNull final String name) {
    super(name);
  }

  @Override
  public String getTaskType() {
    return "theory";
  }
}
