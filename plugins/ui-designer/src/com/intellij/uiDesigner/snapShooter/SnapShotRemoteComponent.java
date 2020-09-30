/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.uiDesigner.snapShooter;

import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class SnapShotRemoteComponent {
  private final int myId;
  private boolean myTopLevel;
  private final String myClassName;
  private final String myLayoutManager;
  private final @Nls String myText;
  private SnapShotRemoteComponent[] myChildren = null;

  public SnapShotRemoteComponent(final int id, final String className, final String layoutManager, final @Nls String text) {
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

  public @NlsSafe String getClassName() {
    return myClassName;
  }

  public @Nls String getText() {
    return myText;
  }

  public @NlsSafe String getLayoutManager() {
    return myLayoutManager;
  }

  public SnapShotRemoteComponent @Nullable [] getChildren() {
    return myChildren;
  }

  public void setChildren(final SnapShotRemoteComponent[] children) {
    myChildren = children;
  }

  public String toProtocolString() {
    return myId + ";" + myClassName + ";" + myLayoutManager + ";" + myText;
  }
}
