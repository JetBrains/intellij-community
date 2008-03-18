package com.intellij.xdebugger.impl.ui.tree;

import com.intellij.xdebugger.impl.ui.tree.nodes.XDebuggerTreeNode;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueContainerNode;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author nik
 */
class XDebuggerTreeRestorer implements XDebuggerTreeListener {
  private final XDebuggerTree myTree;
  private Map<XDebuggerTreeNode, XDebuggerTreeState.NodeInfo> myNode2State = new HashMap<XDebuggerTreeNode, XDebuggerTreeState.NodeInfo>();
  private Map<XValueNodeImpl, XDebuggerTreeState.NodeInfo> myNode2ParentState = new HashMap<XValueNodeImpl, XDebuggerTreeState.NodeInfo>();

  public XDebuggerTreeRestorer(final XDebuggerTree tree) {
    myTree = tree;
    tree.addTreeListener(this);
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

  private void dispose() {
    myNode2ParentState.clear();
    myNode2State.clear();
    myTree.removeTreeListener(this);
  }

}
