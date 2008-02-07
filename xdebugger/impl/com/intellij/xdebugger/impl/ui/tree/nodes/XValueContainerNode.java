package com.intellij.xdebugger.impl.ui.tree.nodes;

import com.intellij.xdebugger.frame.XCompositeNode;
import com.intellij.xdebugger.frame.XValue;
import com.intellij.xdebugger.frame.XValueContainer;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import com.intellij.xdebugger.impl.ui.XDebuggerUIConstants;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.TreeNode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author nik
 */
public abstract class XValueContainerNode<ValueContainer extends XValueContainer> extends XDebuggerTreeNode implements XCompositeNode, TreeNode {
  private List<XValueContainerNode> myChildren;
  private List<MessageTreeNode> myTemporaryChildren;
  protected final ValueContainer myValueContainer;

  protected XValueContainerNode(XDebuggerTree tree, final XDebuggerTreeNode parent, ValueContainer valueContainer) {
    super(tree, parent, true);
    myValueContainer = valueContainer;
    myText.append(XDebuggerUIConstants.COLLECTING_DATA_MESSAGE, XDebuggerUIConstants.COLLECTING_DATA_HIGHLIGHT_ATTRIBUTES);
  }

  private void loadChildren() {
    if (myChildren != null || myTemporaryChildren != null) return;

    myValueContainer.computeChildren(this);
    if (myChildren == null) {
      myTemporaryChildren = Collections.singletonList(MessageTreeNode.createLoadingMessage(myTree, this));
    }
  }

  public void setChildren(final List<XValue> children) {
    DebuggerUIUtil.invokeLater(new Runnable() {
      public void run() {
        myChildren = new ArrayList<XValueContainerNode>();
        for (XValue child : children) {
          myChildren.add(new XValueNodeImpl(myTree, XValueContainerNode.this, child));
        }
        myTemporaryChildren = null;
        fireNodeChildrenChanged();
      }
    });
  }

  protected List<? extends TreeNode> getChildren() {
    loadChildren();

    if (myChildren != null) {
      return myChildren;
    }
    return myTemporaryChildren;
  }

  public ValueContainer getValueContainer() {
    return myValueContainer;
  }

  @Nullable
  public List<XValueContainerNode> getLoadedChildren() {
    return myChildren;
  }
}
