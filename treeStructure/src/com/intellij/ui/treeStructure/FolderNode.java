/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.ui.treeStructure;

import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;

/**
 * @author kir
 */
public class FolderNode extends SimpleNode {

  private final String myFQName;
  private final String myName;

  public FolderNode(FolderNode aParent, String name) {
    super(aParent);
    myName = name;

    final String parentFqn = aParent.myFQName;
    myFQName = "".equals(parentFqn) ? myName : parentFqn + '.' + myName;
    init();
  }

  public FolderNode(Project aProject) {
    this(aProject, null);
  }

  public FolderNode(Project aProject, NodeDescriptor parent) {
    super(aProject, parent);
    myName = "";
    myFQName = "";
    init();
  }

  private void init() {
    setPlainText(myName);
    setIcons(IconLoader.getIcon("/nodes/folder.png"), IconLoader.getIcon("/nodes/folderOpen.png"));
  }

  public final SimpleNode[] getChildren() {
    throw new UnsupportedOperationException("Not Implemented in: " + getClass().getName());
  }

  public Object[] getEqualityObjects() {
    return new Object[]{myFQName, getClass()};
  }

  public String getFullyQualifiedName() {
    return myFQName;
  }
}
