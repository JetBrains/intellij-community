/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.ui.treeStructure;

import javax.swing.tree.DefaultMutableTreeNode;

/**
 * @author peter
 * IBM Rational Software Functional Tester used to test Fabrique requires that every node has
 * public String getText()
 * method to provide a string representation for a node. We delegate it to toString()
 */
public class PatchedDefaultMutableTreeNode extends DefaultMutableTreeNode {
  public PatchedDefaultMutableTreeNode() {
  }

  public PatchedDefaultMutableTreeNode(Object userObject) {
    super(userObject);
  }

  public String getText() {
    return String.valueOf(getUserObject());
  }

}
