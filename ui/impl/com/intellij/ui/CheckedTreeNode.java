package com.intellij.ui;

import javax.swing.tree.DefaultMutableTreeNode;

/**
 * User: lex
 * Date: Sep 20, 2003
 * Time: 5:14:07 PM
 */
public class CheckedTreeNode extends DefaultMutableTreeNode {
  protected boolean isChecked = true;
  private boolean isEnabled = true;
  public CheckedTreeNode(Object userObject) {
    super(userObject);
  }

  public boolean isChecked() {
    return isChecked;
  }


  public void setChecked(boolean checked) {
    isChecked = checked;
  }

  public void setEnabled(final boolean enabled) {
    isEnabled = enabled;
  }

  public boolean isEnabled() {
    return isEnabled;
  }
}
