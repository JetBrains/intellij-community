// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.console.pydev;


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
