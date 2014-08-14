package com.jetbrains.python.edu.ui;

import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.jetbrains.python.edu.StudyTaskManager;

/**
 * author: liana
 * data: 7/29/14.
 */
public class StudyCondition implements Condition, DumbAware {
  public static boolean VALUE = false;
  @Override
  public boolean value(Object o) {
    if (o instanceof Project) {
      Project project = (Project) o;
      StudyTaskManager taskManager = StudyTaskManager.getInstance(project);
      if (taskManager.getCourse() != null) {
        VALUE = true;
      }
    }
    return false;
  }
}
