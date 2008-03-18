package com.intellij.xdebugger.impl.ui.tree;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.xdebugger.impl.ui.tree.nodes.XDebuggerTreeNode;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author nik
 */
public class XDebuggerTreeState {
  private NodeInfo myRootInfo;

  private XDebuggerTreeState(@NotNull XDebuggerTree tree) {
    myRootInfo = new NodeInfo("", "");
    ApplicationManager.getApplication().assertIsDispatchThread();
    addChildren(tree, myRootInfo, ((XDebuggerTreeNode)tree.getTreeModel().getRoot()));
  }

  public void restoreState(@NotNull XDebuggerTree tree) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    XDebuggerTreeRestorer restorer = new XDebuggerTreeRestorer(tree);
    restorer.restoreChildren(((XDebuggerTreeNode)tree.getTreeModel().getRoot()), myRootInfo);
  }

  public static XDebuggerTreeState saveState(@NotNull XDebuggerTree tree) {
    return new XDebuggerTreeState(tree);
  }

  private static void addChildren(final XDebuggerTree tree, final NodeInfo nodeInfo, final XDebuggerTreeNode treeNode) {
    if (tree.isExpanded(treeNode.getPath())) {
      List<? extends XDebuggerTreeNode> children = treeNode.getLoadedChildren();
      if (children != null) {
        nodeInfo.myExpanded = true;
        for (XDebuggerTreeNode child : children) {
          NodeInfo childInfo = createNode(child);
          if (childInfo != null) {
            nodeInfo.addChild(childInfo);
            addChildren(tree, childInfo, child);
          }
        }
      }
    }
  }

  @Nullable
  private static NodeInfo createNode(final XDebuggerTreeNode node) {
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

    public boolean isExpanded() {
      return myExpanded;
    }

    public String getName() {
      return myName;
    }

    public String getValue() {
      return myValue;
    }

    @Nullable
    public NodeInfo getChild(@NotNull String name) {
      return myChidlren != null ? myChidlren.get(name) : null;
    }

    @Nullable
    public NodeInfo removeChild(@NotNull String name) {
      return myChidlren != null ? myChidlren.remove(name) : null;
    }
  }
}
