package com.jetbrains.edu.oldCourseFormat;

import java.util.HashMap;
import java.util.Map;

/**
 * Implementation of task which contains task files, tests, input file for tests
 */
public class Task {
  public String name;
  public Map<String, TaskFile> taskFiles = new HashMap<String, TaskFile>();
  public int myIndex;
}
