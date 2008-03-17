package com.intellij.xdebugger.impl.ui.tree.nodes;

import com.intellij.xdebugger.frame.XCompositeNode;
import com.intellij.xdebugger.frame.XValue;
import com.intellij.xdebugger.frame.XValueContainer;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import com.intellij.xdebugger.impl.ui.XDebuggerUIConstants;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

import javax.swing.tree.TreeNode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author nik
 */
public abstract class XValueContainerNode<ValueContainer extends XValueContainer> extends XDebuggerTreeNode implements XCompositeNode, TreeNode {
  private List<XValueContainerNode<?>> myValueChildren;
  private List<MessageTreeNode> myMessageChildren;
  private List<TreeNode> myCachedAllChildren;
  protected final ValueContainer myValueContainer;
  private volatile boolean myObsolete;

  protected XValueContainerNode(XDebuggerTree tree, final XDebuggerTreeNode parent, ValueContainer valueContainer) {
    super(tree, parent, true);
    myValueContainer = valueContainer;
    myText.append(XDebuggerUIConstants.COLLECTING_DATA_MESSAGE, XDebuggerUIConstants.COLLECTING_DATA_HIGHLIGHT_ATTRIBUTES);
  }

  private void loadChildren() {
    if (myValueChildren != null || myMessageChildren != null) return;

    myCachedAllChildren = null;
    myMessageChildren = Collections.singletonList(createLoadingMessageNode());
    myValueContainer.computeChildren(this);
  }

  protected MessageTreeNode createLoadingMessageNode() {
    return MessageTreeNode.createLoadingMessage(myTree, this);
  }

  public void addChildren(final List<XValue> children, final boolean last) {
    DebuggerUIUtil.invokeLater(new Runnable() {
      public void run() {
        myCachedAllChildren = null;
        if (myValueChildren == null) {
          myValueChildren = new ArrayList<XValueContainerNode<?>>();
        }
        for (XValue child : children) {
          myValueChildren.add(createChildNode(child));
        }
        if (last) {
          myMessageChildren = null;
        }
        fireNodeChildrenChanged();
        myTree.childrenLoaded(XValueContainerNode.this, myValueChildren);
      }
    });
  }

  protected XValueContainerNode<?> createChildNode(final XValue child) {
    return new XValueNodeImpl(myTree, this, child);
  }

  public boolean isObsolete() {
    return myObsolete;
  }

  public void clearChildren() {
    myCachedAllChildren = null;
    myMessageChildren = null;
    myValueChildren = null;
  }

  public void setErrorMessage(final @NotNull String errorMessage) {
    DebuggerUIUtil.invokeLater(new Runnable() {
      public void run() {
        setMessageNode(MessageTreeNode.createErrorMessage(myTree, XValueContainerNode.this, errorMessage));
      }
    });
  }

  protected void setMessageNode(final MessageTreeNode messageNode) {
    myCachedAllChildren = null;
    myMessageChildren = Collections.singletonList(messageNode);
    fireNodeChildrenChanged();
  }

  protected List<? extends TreeNode> getChildren() {
    loadChildren();

    if (myCachedAllChildren == null) {
      myCachedAllChildren = new ArrayList<TreeNode>();
      if (myValueChildren != null) {
        myCachedAllChildren.addAll(myValueChildren);
      }
      if (myMessageChildren != null) {
        myCachedAllChildren.addAll(myMessageChildren);
      }
    }
    return myCachedAllChildren;
  }

  public ValueContainer getValueContainer() {
    return myValueContainer;
  }

  @Nullable
  public List<XValueContainerNode<?>> getLoadedChildren() {
    return myValueChildren;
  }

  public void setObsolete() {
    myObsolete = true;
  }
}
