package com.jetbrains.edu.learning.ui;

import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.Condition;

/**
 * author: liana
 * data: 7/29/14.
 */
public class StudyCondition implements Condition, DumbAware {
  @Override
  public boolean value(Object o) {
    return false;
  }
}
