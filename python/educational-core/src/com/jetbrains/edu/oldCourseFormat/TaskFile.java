package com.jetbrains.edu.oldCourseFormat;

import com.intellij.util.xmlb.annotations.Transient;

import java.util.ArrayList;
import java.util.List;

public class TaskFile {
  public List<TaskWindow> taskWindows = new ArrayList<TaskWindow>();
  @Transient
  public int myIndex = -1;
}
