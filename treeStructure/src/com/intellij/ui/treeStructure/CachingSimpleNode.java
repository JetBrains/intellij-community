/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.ui.treeStructure;

import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.project.Project;

public abstract class CachingSimpleNode extends SimpleNode {

  SimpleNode[] myChildren;

  protected CachingSimpleNode() {
  }

  protected CachingSimpleNode(SimpleNode aParent) {
    super(aParent);
  }

  protected CachingSimpleNode(Project aProject, NodeDescriptor aParentDescriptor) {
    super(aProject, aParentDescriptor);
  }

  public final SimpleNode[] getChildren() {
    if (myChildren == null) {
      myChildren = buildChildren();
    }

    return myChildren;
  }

  protected abstract SimpleNode[] buildChildren();

  public void cleanUpCache() {
    myChildren = null;
  }

  SimpleNode[] getCached() {
    return myChildren;
  }

}
