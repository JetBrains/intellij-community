package com.intellij.xdebugger.impl.ui.tree.nodes;

import com.intellij.xdebugger.frame.XCompositeNode;
import com.intellij.xdebugger.frame.XValue;
import com.intellij.xdebugger.frame.XValueContainer;
import com.intellij.xdebugger.impl.ui.XDebuggerUIConstants;

import javax.swing.tree.TreeNode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author nik
 */
public abstract class XDebuggerTreeNode<ValueContainer extends XValueContainer> extends TreeNodeBase implements XCompositeNode, TreeNode {
  private List<XDebuggerTreeNode> myChildren;
  private List<LoadingMessageTreeNode> myTemporaryChildren;
  protected final ValueContainer myValueContainer;

  protected XDebuggerTreeNode(final TreeNodeBase parent, ValueContainer valueContainer) {
    super(parent, true);
    myValueContainer = valueContainer;
    myText.append(XDebuggerUIConstants.COLLECTING_DATA_MESSAGE, XDebuggerUIConstants.COLLECTING_DATA_HIGHLIGHT_ATTRIBUTES);
  }

  private void loadChildren() {
    if (myChildren != null || myTemporaryChildren != null) return;

    myValueContainer.computeChildren(this);
    if (myChildren == null) {
      myTemporaryChildren = Collections.singletonList(new LoadingMessageTreeNode(this));
    }
  }

  public void setChildren(final List<XValue> children) {
    myChildren = new ArrayList<XDebuggerTreeNode>();
    for (XValue child : children) {
      myChildren.add(new XValueNodeImpl(this, child));
    }
  }

  protected List<? extends TreeNode> getChildren() {
    loadChildren();

    if (myChildren != null) {
      return myChildren;
    }
    return myTemporaryChildren;
  }
}
