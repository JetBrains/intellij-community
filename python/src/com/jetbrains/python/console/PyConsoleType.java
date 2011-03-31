package com.jetbrains.python.console;

import com.jetbrains.python.PyBundle;

/**
 * @author traff
 */
public enum PyConsoleType {
  PYTHON("py", PyBundle.message("python.console")), DJANGO("django", PyBundle.message("django.console"));

  private String myTypeId;
  private String myTitle;

  PyConsoleType(String typeId, String title) {
    myTypeId = typeId;
    myTitle = title;
  }

  public String getTypeId() {
    return myTypeId;
  }

  public String getTitle() {
    return myTitle;
  }
}
