package com.intellij.codeInspection.ex;

import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.util.ui.Tree;
import com.intellij.util.ui.tree.TreeUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.util.*;

/**
 * User: anna
 * Date: Dec 18, 2004
 */
public class VisibleTreeState implements JDOMExternalizable {

  @NonNls private static final String EXPANDED = "expanded_node";
  @NonNls private static final String SELECTED = "selected_node";
  @NonNls private static final String NAME = "name";

  private HashSet<String> myExpandedNodes = new HashSet<String>();
  private HashSet<String> mySelectedNodes = new HashSet<String>();

  public VisibleTreeState(VisibleTreeState src) {
    myExpandedNodes.addAll(src.myExpandedNodes);
    mySelectedNodes.addAll(src.mySelectedNodes);
  }

  public VisibleTreeState() {
  }

  public void expandNode(String nodeTitle) {
    myExpandedNodes.add(nodeTitle);
  }

  public void collapseNode(String nodeTitle) {
    myExpandedNodes.remove(nodeTitle);
  }

  public void restoreVisibleState(Tree tree) {
    ArrayList<TreePath> pathsToExpand = new ArrayList<TreePath>();
    ArrayList<TreePath> toSelect = new ArrayList<TreePath>();
    traverseNodes((DefaultMutableTreeNode)tree.getModel().getRoot(), pathsToExpand, toSelect);
    TreeUtil.restoreExpandedPaths(tree, pathsToExpand);
    if (toSelect.isEmpty()) {
      TreeUtil.selectFirstNode(tree);
    }
    else {
      for (Iterator<TreePath> iterator = toSelect.iterator(); iterator.hasNext();) {
        TreeUtil.selectPath(tree, iterator.next());
      }
    }
  }

  private void traverseNodes(final DefaultMutableTreeNode root, List<TreePath> pathsToExpand, List<TreePath> toSelect) {
    final Object userObject = root.getUserObject();
    final TreeNode[] rootPath = ((DefaultMutableTreeNode)root).getPath();
    if (userObject instanceof Descriptor) {
      final String displayName = ((Descriptor)userObject).getText();
      if (mySelectedNodes.contains(displayName)) {
        toSelect.add(new TreePath(rootPath));
      }
      if (myExpandedNodes.contains(displayName)) {
        pathsToExpand.add(new TreePath(rootPath));
      }
    }
    else {
      if (mySelectedNodes.contains(userObject)) {
        toSelect.add(new TreePath(rootPath));
      }
      if (myExpandedNodes.contains(userObject)) {
        pathsToExpand.add(new TreePath(rootPath));
      }
      for (int i = 0; i < root.getChildCount(); i++) {
        traverseNodes((DefaultMutableTreeNode)root.getChildAt(i), pathsToExpand, toSelect);
      }
    }
  }

  public void saveVisibleState(Tree tree) {
    myExpandedNodes.clear();
    final DefaultMutableTreeNode rootNode = (DefaultMutableTreeNode)tree.getModel().getRoot();
    Enumeration expanded = tree.getExpandedDescendants(new TreePath(rootNode.getPath()));
    if (expanded != null) {
      while (expanded.hasMoreElements()) {
        final TreePath treePath = (TreePath)expanded.nextElement();
        final DefaultMutableTreeNode node = (DefaultMutableTreeNode)treePath.getLastPathComponent();
        String expandedNode;
        if (node.getUserObject() instanceof Descriptor) {
          expandedNode = ((Descriptor)node.getUserObject()).getText();
        }
        else {
          expandedNode = (String)node.getUserObject();
        }
        myExpandedNodes.add(expandedNode);
      }
    }
    mySelectedNodes.clear();
    final TreePath[] selectionPaths = tree.getSelectionPaths();
    for (int i = 0; selectionPaths != null && i < selectionPaths.length; i++) {
      final DefaultMutableTreeNode node = (DefaultMutableTreeNode)selectionPaths[i].getLastPathComponent();
      String selectedNode;
      if (node.getUserObject() instanceof Descriptor) {
        selectedNode = ((Descriptor)node.getUserObject()).getText();
      }
      else {
        selectedNode = (String)node.getUserObject();
      }
      mySelectedNodes.add(selectedNode);
    }
  }


  public void readExternal(Element element) throws InvalidDataException {
    myExpandedNodes.clear();
    for (Iterator<Element> iterator = element.getChildren(EXPANDED).iterator(); iterator.hasNext();) {
      myExpandedNodes.add(iterator.next().getAttributeValue(NAME));
    }
    mySelectedNodes.clear();
    for (Iterator<Element> iterator = element.getChildren(SELECTED).iterator(); iterator.hasNext();) {
      mySelectedNodes.add(iterator.next().getAttributeValue(NAME));
    }
  }

  public void writeExternal(Element element) throws WriteExternalException {
    for (Iterator<String> iterator = myExpandedNodes.iterator(); iterator.hasNext();) {
      final String expandedNode = iterator.next();
      Element exp = new Element(EXPANDED);
      exp.setAttribute(NAME, expandedNode);
      element.addContent(exp);
    }
    for (Iterator<String> iterator = mySelectedNodes.iterator(); iterator.hasNext();) {
      final String selectedNode = iterator.next();
      Element exp = new Element(SELECTED);
      exp.setAttribute(NAME, selectedNode);
      element.addContent(exp);
    }
  }

  public boolean compare(Object object) {
    if (!(object instanceof VisibleTreeState)) return false;
    final VisibleTreeState that = (VisibleTreeState)object;
    if (myExpandedNodes == null && that.myExpandedNodes != null) {
      return false;
    }
    if (myExpandedNodes.size() != that.myExpandedNodes.size()) return false;
    for (Iterator<String> iterator = myExpandedNodes.iterator(); iterator.hasNext();) {
      if (!that.myExpandedNodes.contains(iterator.next())) {
        return false;
      }
    }
    if (mySelectedNodes == null) {
      return that.mySelectedNodes == null;
    }
    if (mySelectedNodes.size() != that.mySelectedNodes.size()) return false;
    for (Iterator<String> iterator = mySelectedNodes.iterator(); iterator.hasNext();) {
      if (!that.mySelectedNodes.contains(iterator.next())) {
        return false;
      }
    }
    return true;
  }
}
