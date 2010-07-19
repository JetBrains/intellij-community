package com.jetbrains.python.console.pydev;

/**
 * @author yole
 */
public class PydevCompletionVariant {
  private final String myName;
  private final String myDescription;
  private final String myArgs;
  private final int myType;

  public PydevCompletionVariant(String name, String description, String args, int type) {
    myName = name;
    myDescription = description;
    myArgs = args;
    myType = type;
  }

  public String getName() {
    return myName;
  }

  public String getDescription() {
    return myDescription;
  }

  public String getArgs() {
    return myArgs;
  }

  public int getType() {
    return myType;
  }
}
