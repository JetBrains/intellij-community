package com.intellij.openapi.roots.ui.configuration.packaging;

import com.intellij.util.ui.Tree;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.TreePath;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;

/**
 * @author nik
 */
public class PackagingTreeState {
  private PackagingNodeState myRootState;
  private List<String[]> mySelectedPaths;

  private PackagingTreeState(Tree tree) {
    myRootState = saveState(tree, (PackagingTreeNode)tree.getModel().getRoot());
    mySelectedPaths = new ArrayList<String[]>();
    TreePath[] treePaths = tree.getSelectionPaths();
    if (treePaths != null) {
      for (TreePath path : treePaths) {
        Object[] pathElements = path.getPath();
        String[] names = new String[pathElements.length];
        for (int i = 0; i < pathElements.length; i++) {
          names[i] = ((PackagingTreeNode)pathElements[i]).getOutputFileName();
        }
        mySelectedPaths.add(names);
      }
    }
  }

  public static PackagingTreeState saveState(Tree tree) {
    return new PackagingTreeState(tree);
  }

  public void restoreState(Tree tree) {
    if (myRootState != null) {
      expandNodes(tree, (PackagingTreeNode)tree.getModel().getRoot(), myRootState);
    }
    
    List<TreePath> paths = new ArrayList<TreePath>();
    for (String[] selectedPath : mySelectedPaths) {
      PackagingTreeNode node = (PackagingTreeNode)tree.getModel().getRoot();
      TreePath path = new TreePath(node);
      for (int i = 1; i < selectedPath.length; i++) {
        node = node.findChildByName(selectedPath[i]);
        if (node == null) break;
        path = path.pathByAddingChild(node);
      }
      if (path.getPathCount() > 1) {
        paths.add(path);
      }
    }
    tree.setSelectionPaths(paths.toArray(new TreePath[paths.size()]));
  }

  private static void expandNodes(final Tree tree, final PackagingTreeNode node, final PackagingNodeState nodeState) {
    TreePath path = new TreePath(node.getPath());
    if (!tree.isExpanded(path)) {
      tree.expandPath(path);
    }
    for (PackagingTreeNode child : node.getChildren()) {
      PackagingNodeState childState = nodeState.findChild(child.getOutputFileName());
      if (childState != null) {
        expandNodes(tree, child, childState);
      }
    }
  }

  @Nullable
  private static PackagingNodeState saveState(final Tree tree, final PackagingTreeNode node) {
    if (tree.isExpanded(new TreePath(node.getPath()))) {
      List<PackagingTreeNode> children = node.getChildren();
      if (!children.isEmpty()) {
        PackagingNodeState nodeState = new PackagingNodeState(node.getOutputFileName());
        for (PackagingTreeNode child : children) {
          PackagingNodeState childState = saveState(tree, child);
          if (childState != null) {
            nodeState.addChild(childState);
          }
        }
        return nodeState;
      }
    }
    return null;
  }

  private static class PackagingNodeState {
    private String myName;
    private Map<String, PackagingNodeState> myChildren;

    private PackagingNodeState(final String name) {
      myName = name;
    }

    public void addChild(@NotNull PackagingNodeState childState) {
      if (myChildren == null) {
        myChildren = new HashMap<String, PackagingNodeState>();
      }
      myChildren.put(childState.myName, childState);
    }

    public boolean isExpanded() {
      return myChildren != null && !myChildren.isEmpty();
    }

    @Nullable
    private PackagingNodeState findChild(String name) {
      return myChildren != null ? myChildren.get(name) : null;
    }
  }
}
