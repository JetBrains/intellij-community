package com.jetbrains.python.console;

import com.jetbrains.python.PyBundle;

/**
 * @author traff
 */
public class PyConsoleType {
  public static PyConsoleType PYTHON = new PyConsoleType("py", PyBundle.message("python.console"));

  private String myTypeId;
  private String myTitle;

  public PyConsoleType(String typeId, String title) {
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
