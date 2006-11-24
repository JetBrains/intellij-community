/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

/*
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 15.11.2006
 * Time: 20:04:24
 */
package com.intellij.openapi.diff.impl.patch;

public class PatchLine {
  public enum Type { CONTEXT, ADD, REMOVE }

  private Type myType;
  private String myText;
  private boolean mySuppressNewLine;

  public PatchLine(final Type type, final String text) {
    myType = type;
    myText = text;
  }

  public Type getType() {
    return myType;
  }

  public String getText() {
    return myText;
  }

  public boolean isSuppressNewLine() {
    return mySuppressNewLine;
  }

  public void setSuppressNewLine(final boolean suppressNewLine) {
    mySuppressNewLine = suppressNewLine;
  }
}