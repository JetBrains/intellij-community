package com.jetbrains.edu.coursecreator.actions.oldCourseFormat;

import java.util.HashMap;
import java.util.Map;

/**
 * Implementation of task which contains task files, tests, input file for tests
 */
public class OldTask {
  public String name;
  public Map<String, OldTaskFile> taskFiles = new HashMap<String, OldTaskFile>();
  public int myIndex;
}
