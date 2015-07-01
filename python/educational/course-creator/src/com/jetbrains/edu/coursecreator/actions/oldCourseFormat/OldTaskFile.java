package com.jetbrains.edu.coursecreator.actions.oldCourseFormat;

import com.intellij.util.xmlb.annotations.Transient;

import java.util.ArrayList;
import java.util.List;

public class OldTaskFile {
  public List<OldTaskWindow> taskWindows = new ArrayList<OldTaskWindow>();
  @Transient
  public int myIndex = -1;
}
