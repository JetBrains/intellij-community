/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.snapShooter;

import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class SnapShotRemoteComponent {
  private int myId;
  private boolean myTopLevel;
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

  public SnapShotRemoteComponent(String line, boolean topLevel) {
    final String[] strings = line.trim().split(";", 4);
    myId = Integer.parseInt(strings [0]);
    myClassName = strings [1];
    myLayoutManager = strings [2];
    myText = strings [3];
    myTopLevel = topLevel;
  }

  public int getId() {
    return myId;
  }

  public boolean isTopLevel() {
    return myTopLevel;
  }

  public String getClassName() {
    return myClassName;
  }

  public String getText() {
    return myText;
  }

  public String getLayoutManager() {
    return myLayoutManager;
  }

  @Nullable
  public SnapShotRemoteComponent[] getChildren() {
    return myChildren;
  }

  public void setChildren(final SnapShotRemoteComponent[] children) {
    myChildren = children;
  }

  public String toProtocolString() {
    return myId + ";" + myClassName + ";" + myLayoutManager + ";" + myText;
  }
}
