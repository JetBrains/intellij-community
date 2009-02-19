package com.intellij.xdebugger.impl.ui.tree;

import com.intellij.xdebugger.impl.ui.tree.nodes.XDebuggerTreeNode;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueContainerNode;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import org.jetbrains.annotations.NotNull;

import javax.swing.event.TreeSelectionListener;
import javax.swing.event.TreeSelectionEvent;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.awt.*;

/**
 * @author nik
 */
public class XDebuggerTreeRestorer implements XDebuggerTreeListener, TreeSelectionListener {
  private final XDebuggerTree myTree;
  private final Rectangle myLastVisibleNodeRect;
  private final Map<XDebuggerTreeNode, XDebuggerTreeState.NodeInfo> myNode2State = new HashMap<XDebuggerTreeNode, XDebuggerTreeState.NodeInfo>();
  private final Map<XValueNodeImpl, XDebuggerTreeState.NodeInfo> myNode2ParentState = new HashMap<XValueNodeImpl, XDebuggerTreeState.NodeInfo>();
  private boolean myStopRestoringSelection;
  private boolean myInsideRestoring;

  public XDebuggerTreeRestorer(final XDebuggerTree tree, Rectangle lastVisibleNodeRect) {
    myTree = tree;
    myLastVisibleNodeRect = lastVisibleNodeRect;
    tree.addTreeListener(this);
    tree.addTreeSelectionListener(this);
  }

  public void restoreChildren(final XDebuggerTreeNode treeNode, final XDebuggerTreeState.NodeInfo nodeInfo) {
    if (nodeInfo.isExpanded()) {
      myTree.expandPath(treeNode.getPath());
      List<? extends XDebuggerTreeNode> children = treeNode.getLoadedChildren();
      if (children != null) {
        for (XDebuggerTreeNode child : children) {
          restoreNode(child, nodeInfo);
        }
      }
      myNode2State.put(treeNode, nodeInfo);
    }
  }

  private void restoreNode(final XDebuggerTreeNode treeNode, final XDebuggerTreeState.NodeInfo parentInfo) {
    if (treeNode instanceof XValueNodeImpl) {
      XValueNodeImpl node = (XValueNodeImpl)treeNode;
      String nodeName = node.getName();
      String nodeValue = node.getValue();
      if (nodeName == null || nodeValue == null) {
        myNode2ParentState.put(node, parentInfo);
      }
      else {
        doRestoreNode(node, parentInfo, nodeName, nodeValue);
      }
    }
  }

  private void doRestoreNode(final XValueNodeImpl treeNode, final XDebuggerTreeState.NodeInfo parentInfo, final String nodeName,
                             final String nodeValue) {
    XDebuggerTreeState.NodeInfo childInfo = parentInfo.removeChild(nodeName);
    if (childInfo != null) {
      if (!childInfo.getValue().equals(nodeValue)) {
        treeNode.markChanged();
      }
      if (!myStopRestoringSelection && childInfo.isSelected()) {
        try {
          myInsideRestoring = true;
          myTree.addSelectionPath(treeNode.getPath());
        }
        finally {
          myInsideRestoring = false;
        }
      }

      restoreChildren(treeNode, childInfo);
    }
  }

  public void nodeLoaded(@NotNull final XValueNodeImpl node, final String name, final String value) {
    XDebuggerTreeState.NodeInfo parentInfo = myNode2ParentState.remove(node);
    if (parentInfo != null) {
      doRestoreNode(node, parentInfo, name, value);
    }
    disposeIfFinished();
  }

  private void disposeIfFinished() {
    if (myNode2ParentState.isEmpty() && myNode2State.isEmpty()) {
      if (myLastVisibleNodeRect != null) {
        myTree.scrollRectToVisible(myLastVisibleNodeRect);
      }
      dispose();
    }
  }

  public void childrenLoaded(@NotNull final XDebuggerTreeNode node, @NotNull final List<XValueContainerNode<?>> children, final boolean last) {
    XDebuggerTreeState.NodeInfo nodeInfo = myNode2State.get(node);
    if (nodeInfo != null) {
      for (XDebuggerTreeNode child : children) {
        restoreNode(child, nodeInfo);
      }
    }
    if (last) {
      myNode2State.remove(node);
      disposeIfFinished();
    }
  }

  public void dispose() {
    myNode2ParentState.clear();
    myNode2State.clear();
    myTree.removeTreeListener(this);
    myTree.removeTreeSelectionListener(this);
  }

  public void valueChanged(TreeSelectionEvent e) {
    if (!myInsideRestoring) {
      myStopRestoringSelection = true;
    }
  }
}
