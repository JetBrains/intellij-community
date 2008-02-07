package com.intellij.xdebugger.impl.ui.tree;

import com.intellij.xdebugger.impl.ui.tree.nodes.XDebuggerTreeNode;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueContainerNode;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

import javax.swing.tree.TreePath;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author nik
 */
public class DebuggerTreeState {
  private NodeInfo myRootInfo;

  public DebuggerTreeState(XDebuggerTree tree) {
    myRootInfo = new NodeInfo("", "");
    ApplicationManager.getApplication().assertIsDispatchThread();
    addChildren(tree, myRootInfo, ((XDebuggerTreeNode)tree.getTreeModel().getRoot()));
  }

  private static void addChildren(final XDebuggerTree tree, final NodeInfo nodeInfo, final XDebuggerTreeNode treeNode) {
    if (treeNode instanceof XValueContainerNode && tree.isExpanded(new TreePath(treeNode.getPath()))) {
      List<XValueContainerNode> children = ((XValueContainerNode<?>)treeNode).getLoadedChildren();
      if (children != null) {
        nodeInfo.myExpanded = true;
        for (XValueContainerNode child : children) {
          NodeInfo node = createNode(child);
          if (node != null) {
            nodeInfo.addChild(node);
          }
        }
      }
    }
  }

  @Nullable
  private static NodeInfo createNode(final XValueContainerNode node) {
    if (node instanceof XValueNodeImpl) {
      XValueNodeImpl valueNode = (XValueNodeImpl)node;
      String name = valueNode.getName();
      String value = valueNode.getValue();
      if (name != null && value != null) {
        return new NodeInfo(name, value);
      }
    }
    return null;
  }

  public static class NodeInfo {
    private String myName;
    private String myValue;
    private boolean myExpanded;
    private Map<String, NodeInfo> myChidlren;

    public NodeInfo(final String name, final String value) {
      myName = name;
      myValue = value;
    }

    public void addChild(@NotNull NodeInfo child) {
      if (myChidlren == null) {
        myChidlren = new HashMap<String, NodeInfo>();
      }
      myChidlren.put(child.myName, child);
    }
  }
}
