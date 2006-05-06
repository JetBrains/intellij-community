/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.ui.treeStructure;

public class NullNode extends SimpleNode {
  public NullNode() {
    super();
  }

  public SimpleNode[] getChildren() {
    return NO_CHILDREN;
  }

  public Object[] getEqualityObjects() {
    return NONE;
  }
}
