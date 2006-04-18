/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.snapShooter;

/**
 * @author yole
 */
public class SnapShotRemoteComponent {
  private int myId;
  private String myClassName;
  private final String myLayoutManager;
  private String myText;
  private SnapShotRemoteComponent[] myChildren = null;

  public SnapShotRemoteComponent(final int id, final String className, final String layoutManager, final String text) {
    myId = id;
    myClassName = className;
    myLayoutManager = layoutManager;
    myText = text;
  }

  public SnapShotRemoteComponent(String line) {
    final String[] strings = line.trim().split(";", 4);
    myId = Integer.parseInt(strings [0]);
    myClassName = strings [1];
    myLayoutManager = strings [2];
    myText = strings [3];
  }

  public int getId() {
    return myId;
  }

  public String getClassName() {
    return myClassName;
  }

  public String getText() {
    return myText;
  }

  public SnapShotRemoteComponent[] getChildren() {
    return myChildren;
  }

  public void setChildren(final SnapShotRemoteComponent[] children) {
    myChildren = children;
  }

  @Override
  public String toString() {
    StringBuilder resultBuilder = new StringBuilder();
    resultBuilder.append(myClassName);
    if (myText.length() > 0) {
      resultBuilder.append(" \"").append(myText).append("\"");
    }
    if (myLayoutManager.length() > 0) {
      resultBuilder.append(" (").append(myLayoutManager).append(")");
    }
    return resultBuilder.toString();
  }

  public String toProtocolString() {
    return myId + ";" + myClassName + ";" + myLayoutManager + ";" + myText;
  }
}
