package com.jetbrains.edu.learning.courseFormat.tasks;

import org.jetbrains.annotations.NotNull;

/**
 * Original PyCharm Edu tasks with local tests and answer placeholders
 */
public class PyCharmTask extends Task {

  public PyCharmTask() {
  }

  public PyCharmTask(@NotNull String name) {
    super(name);
  }

  @Override
  public String getTaskType() {
    return "pycharm";
  }
}