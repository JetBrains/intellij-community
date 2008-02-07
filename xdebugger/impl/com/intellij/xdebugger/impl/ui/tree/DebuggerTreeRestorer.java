package com.intellij.xdebugger.impl.ui.tree;

import com.intellij.xdebugger.impl.ui.tree.nodes.XDebuggerTreeNode;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueContainerNode;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * @author nik
 */
public class DebuggerTreeRestorer implements XDebuggerTreeListener {
  private final XDebuggerTree myTree;
  private Map<XValueContainerNode<?>, DebuggerTreeState.NodeInfo> myNode2State = new HashMap<XValueContainerNode<?>, DebuggerTreeState.NodeInfo>();
  private Map<XValueNodeImpl, DebuggerTreeState.NodeInfo> myNode2ParentState = new HashMap<XValueNodeImpl, DebuggerTreeState.NodeInfo>();

  public DebuggerTreeRestorer(final XDebuggerTree tree) {
    myTree = tree;
    tree.addTreeListener(this);
  }

  public void restoreChildren(final XDebuggerTreeNode treeNode, final DebuggerTreeState.NodeInfo nodeInfo) {
    if (treeNode instanceof XValueContainerNode<?> && nodeInfo.isExpanded()) {
      myTree.expandPath(treeNode.getPath());
      XValueContainerNode<?> node = (XValueContainerNode<?>)treeNode;
      List<XValueContainerNode<?>> children = node.getLoadedChildren();
      if (children != null) {
        for (XValueContainerNode<?> child : children) {
          restoreNode(child, nodeInfo);
        }
      }
      else {
        myNode2State.put(node, nodeInfo);
      }
    }
  }

  private void restoreNode(final XValueContainerNode<?> treeNode, final DebuggerTreeState.NodeInfo parentInfo) {
    if (treeNode instanceof XValueNodeImpl) {
      XValueNodeImpl node = (XValueNodeImpl)treeNode;
      String nodeName = node.getName();
      String nodeValue = node.getValue();
      if (nodeName == null || nodeValue == null) {
        myNode2ParentState.put(node, parentInfo);
      }
      else {
        doRestoreNode(treeNode, parentInfo, nodeName);
      }
    }
  }

  private void doRestoreNode(final XValueContainerNode<?> treeNode, final DebuggerTreeState.NodeInfo parentInfo, final String nodeName) {
    DebuggerTreeState.NodeInfo childInfo = parentInfo.getChild(nodeName);
    if (childInfo != null) {
      restoreChildren(treeNode, childInfo);
    }
  }

  public void nodeLoaded(@NotNull final XValueNodeImpl node, final String name, final String value) {
    DebuggerTreeState.NodeInfo parentInfo = myNode2ParentState.get(node);
    if (parentInfo != null) {
      doRestoreNode(node, parentInfo, name);
    }
  }

  public void childrenLoaded(@NotNull final XValueContainerNode<?> node, @NotNull final List<XValueContainerNode<?>> children) {
    DebuggerTreeState.NodeInfo nodeInfo = myNode2State.get(node);
    if (nodeInfo != null) {
      for (XValueContainerNode<?> child : children) {
        restoreNode(child, nodeInfo);
      }
    }
  }
}
