package com.intellij.ui;

import javax.swing.tree.DefaultMutableTreeNode;

/**
 * User: lex
 * Date: Sep 20, 2003
 * Time: 5:14:07 PM
 */
public class CheckedTreeNode extends DefaultMutableTreeNode {
  private boolean isChecked = true;
  public CheckedTreeNode(Object userObject) {
    super(userObject);
  }

  public boolean isChecked() {
    return isChecked;
  }


  public void setChecked(boolean checked) {
    isChecked = checked;
  }
}
