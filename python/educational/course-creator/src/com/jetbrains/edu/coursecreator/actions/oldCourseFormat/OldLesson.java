package com.jetbrains.edu.coursecreator.actions.oldCourseFormat;

import java.util.ArrayList;
import java.util.List;

public class OldLesson {
  public String name;
  public List<OldTask> taskList = new ArrayList<OldTask>();
  public int myIndex = -1;
  public OldLessonInfo myLessonInfo = new OldLessonInfo();

}
