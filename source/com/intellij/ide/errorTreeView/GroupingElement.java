/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.ide.errorTreeView;

import com.intellij.openapi.vfs.VirtualFile;

import javax.swing.*;

/**
 * @author Eugene Zhuravlev
 *         Date: Nov 12, 2004
 */
public class GroupingElement extends ErrorTreeElement {
  private final String[] myText;
  private final Object myData;

  public GroupingElement(VirtualFile file) {
    this(file.getPresentableUrl(), null);
  }

  public GroupingElement(String name, Object data) {
    super(ErrorTreeElementKind.GENERIC);
    myText = new String[] {name};
    myData = data;
  }

  public GroupingElement(VirtualFile file, Object data) {
    this(file.getPresentableUrl(), data);
  }

  public Object getData() {
    return myData;
  }

  public String[] getText() {
    return myText;
  }

  public String getName() {
    return myText[0];
  }

  public String getExportTextPrefix() {
    return "";
  }
}
