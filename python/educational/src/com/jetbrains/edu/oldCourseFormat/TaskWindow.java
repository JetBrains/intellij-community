package com.jetbrains.edu.oldCourseFormat;

import com.jetbrains.edu.courseFormat.StudyStatus;

/**
 * Implementation of windows which user should type in
 */


public class TaskWindow {
  public int line = 0;
  public int start = 0;
  public String hint = "";
  public String possibleAnswer = "";
  public int length = 0;
  public int myIndex = -1;
  public int myInitialLine = -1;
  public int myInitialStart = -1;
  public int myInitialLength = -1;
  public StudyStatus myStatus = StudyStatus.Unchecked;
}