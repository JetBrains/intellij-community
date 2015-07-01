package com.jetbrains.edu.coursecreator.actions.oldCourseFormat;

import java.util.ArrayList;
import java.util.List;

public class OldCourse {

  public List<OldLesson> lessons = new ArrayList<OldLesson>();
  public String description;
  public String name;
  public String myResourcePath = "";
  public String author;
  public boolean myUpToDate = false;

}