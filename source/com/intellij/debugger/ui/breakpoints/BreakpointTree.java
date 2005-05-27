/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.debugger.ui.breakpoints;
import com.intellij.ui.*;
import com.intellij.util.Icons;
import com.intellij.util.containers.Convertor;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.util.*;

/**
 * @author Eugene Zhuravlev
 *         Date: May 20, 2005
 */
public class BreakpointTree extends CheckboxTree {
  private final CheckedTreeNode myRootNode;
  private final List<Breakpoint> myBreakpoints = new ArrayList<Breakpoint>();
  private final Map<TreeDescriptor, CheckedTreeNode> myDescriptorToNodeMap = new HashMap<TreeDescriptor, CheckedTreeNode>();
  
  private boolean myGroupByMethods = false;
  private boolean myGroupByClasses = true;
  private boolean myFlattenPackages = true;
  
  private final NodeAppender[] myAppenders = new NodeAppender[] {
    new BreakpointToMethodAppender(),
    new BreakpointToClassAppender(),
    new BreakpointToPackageAppender(),
    new MethodToClassAppender(),
    new MethodToPackageAppender(),
    new ClassToPackageAppender(),
    new PackageToPackageAppender(),
  };

  protected void installSpeedSearch() {
    new TreeSpeedSearch(this, new Convertor<TreePath, String>() {
      public String convert(TreePath path) {
        final CheckedTreeNode node = (CheckedTreeNode)path.getLastPathComponent();
        return ((TreeDescriptor)node.getUserObject()).getDisplayString();
      }
    });
  }

  public boolean getExpandsSelectedPaths() {
    return true;
  }

  public Breakpoint[] getSelectedBreakpoints() {
    final TreePath[] selectionPaths = getSelectionPaths();
    if (selectionPaths == null || selectionPaths.length == 0) {
      return Breakpoint.EMPTY_ARRAY;
    }
    final List<Breakpoint> breakpoints = new ArrayList<Breakpoint>(selectionPaths.length);
    for (TreePath path : selectionPaths) {
      final CheckedTreeNode node = (CheckedTreeNode)path.getLastPathComponent();
      TreeUtil.traverseDepth(node, new TreeUtil.Traverse() {
        public boolean accept(Object _node) {
          final CheckedTreeNode node = (CheckedTreeNode)_node;
          final TreeDescriptor descriptor = (TreeDescriptor)node.getUserObject();
          if (descriptor instanceof BreakpointDescriptor) {
            breakpoints.add(((BreakpointDescriptor)descriptor).getBreakpoint());
          }
          return true;
        }
      });
    }
    return breakpoints.toArray(new Breakpoint[breakpoints.size()]);
  }

  public void selectBreakpoint(Breakpoint breakpoint) {
    final CheckedTreeNode node = myDescriptorToNodeMap.get(new BreakpointDescriptor(breakpoint));
    if (node == null) {
      return;
    }
    TreeUtil.selectNode(this, node);
  }

  public void selectFirstBreakpoint() {
    TreeUtil.traverseDepth(myRootNode, new TreeUtil.Traverse() {
      public boolean accept(Object node) {
        final CheckedTreeNode treeNode = (CheckedTreeNode)node;
        final TreeDescriptor descriptor = (TreeDescriptor)treeNode.getUserObject();
        if (descriptor instanceof BreakpointDescriptor) {
          TreeUtil.selectNode(BreakpointTree.this, treeNode);
          return false;
        }
        return true;
      }
    });
  }

  public List<Breakpoint> getBreakpoints() {
    return Collections.unmodifiableList(myBreakpoints);
  }

  private abstract static class TreeDescriptor {
    protected void customizeCellRenderer(final ColoredTreeCellRenderer targetRenderer, CheckedTreeNode node, boolean selected, final boolean checked, boolean expanded, boolean leaf, boolean hasFocus) {
      targetRenderer.setIcon(getDisplayIcon());
      targetRenderer.append(getDisplayString(), checked? SimpleTextAttributes.SIMPLE_CELL_ATTRIBUTES : SimpleTextAttributes.GRAYED_ATTRIBUTES);
    }

    protected abstract String getDisplayString();

    protected abstract Icon getDisplayIcon();

  }

  private static final class BreakpointDescriptor extends TreeDescriptor {
    private final Breakpoint myBreakpoint;
    public BreakpointDescriptor(Breakpoint breakpoint) {
      myBreakpoint = breakpoint;
    }
    public @NotNull Breakpoint getBreakpoint() {
      return myBreakpoint;
    }

    protected Icon getDisplayIcon() {
      return (myBreakpoint instanceof BreakpointWithHighlighter)?
        myBreakpoint.ENABLED? ((BreakpointWithHighlighter)myBreakpoint).getSetIcon() : ((BreakpointWithHighlighter)myBreakpoint).getDisabledIcon() :
        myBreakpoint.getIcon();
    }

    public String getDisplayString() {
      return myBreakpoint.getDisplayName();
    }

    public boolean equals(final Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      final BreakpointDescriptor breakpointDescriptor = (BreakpointDescriptor)o;
      return myBreakpoint.equals(breakpointDescriptor.myBreakpoint);
    }

    public int hashCode() {
      return myBreakpoint.hashCode();
    }
  }

  private static final class MethodDescriptor extends TreeDescriptor {
    private final String myClassName;
    private final String myMethodName;

    public MethodDescriptor(String methodName, String className) {
      myClassName = className;
      myMethodName = methodName;
    }

    public String getClassName() {
      return myClassName;
    }

    public String getMethodName() {
      return myMethodName;
    }

    protected String getDisplayString() {
      return myMethodName;
    }

    protected Icon getDisplayIcon() {
      return Icons.METHOD_ICON;
    }

    public boolean equals(final Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      final MethodDescriptor methodDescriptor = (MethodDescriptor)o;
      if (!myClassName.equals(methodDescriptor.myClassName)) {
        return false;
      }
      return myMethodName.equals(methodDescriptor.myMethodName);
    }

    public int hashCode() {
      int result;
      result = myClassName.hashCode();
      result = 29 * result + myMethodName.hashCode();
      return result;
    }
  }

  private static final class ClassDescriptor extends TreeDescriptor {
    private final String myClassName;

    public ClassDescriptor(String className) {
      myClassName = className;
    }

    public String getPackageName() {
      final int dotIndex = myClassName.lastIndexOf('.');
      return dotIndex >= 0 ? myClassName.substring(0, dotIndex) : "<Default>";
    }

    public String getClassName() {
      return myClassName;
    }

    protected String getDisplayString() {
      final int dotIndex = myClassName.lastIndexOf('.');
      return dotIndex >= 0 && dotIndex + 1 < myClassName.length()? myClassName.substring(dotIndex + 1) : myClassName;
    }

    protected Icon getDisplayIcon() {
      return Icons.CLASS_ICON;
    }

    public boolean equals(final Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }

      final ClassDescriptor classDescriptor = (ClassDescriptor)o;

      return myClassName.equals(classDescriptor.myClassName);

    }

    public int hashCode() {
      return myClassName.hashCode();
    }
  }

  private static final class PackageDescriptor extends TreeDescriptor {
    private final String myPackageName;

    public PackageDescriptor(String packageName) {
      myPackageName = packageName;
    }

    public String getPackageName() {
      return myPackageName;
    }

    public String getParentPackageName() {
      final int dotIndex = myPackageName.lastIndexOf('.');
      return dotIndex >= 0 ? myPackageName.substring(0, dotIndex) : null;
    }

    public void customizeCellRenderer(final ColoredTreeCellRenderer targetRenderer, CheckedTreeNode node, boolean selected,
                                      final boolean checked, boolean expanded, boolean leaf, boolean hasFocus) {
      targetRenderer.setIcon(Icons.PACKAGE_ICON);
      final String displayName;
      final CheckedTreeNode parent = (CheckedTreeNode)node.getParent();
      if (parent != null && parent.getUserObject() instanceof PackageDescriptor) {
        final String parentPackageInTree = ((PackageDescriptor)parent.getUserObject()).getPackageName() + ".";
        displayName = myPackageName.startsWith(parentPackageInTree)? myPackageName.substring(parentPackageInTree.length()) : myPackageName;
      }
      else {
        displayName = myPackageName;
      }
      targetRenderer.append(displayName, checked? SimpleTextAttributes.SIMPLE_CELL_ATTRIBUTES : SimpleTextAttributes.GRAYED_ATTRIBUTES);
    }

    protected String getDisplayString() {
      return myPackageName;
    }

    protected Icon getDisplayIcon() {
      return Icons.PACKAGE_ICON;
    }

    public boolean equals(final Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }

      final PackageDescriptor packageDescriptor = (PackageDescriptor)o;

      return myPackageName.equals(packageDescriptor.myPackageName);

    }

    public int hashCode() {
      return myPackageName.hashCode();
    }
  }

  public BreakpointTree() {
    super(new BreakpointTreeCellRenderer(), new CheckedTreeNode(new RootDescriptor()));
    myRootNode = (CheckedTreeNode)getModel().getRoot();
    myDescriptorToNodeMap.put((TreeDescriptor)myRootNode.getUserObject(), myRootNode);
  }

  public boolean isGroupByMethods() {
    return myGroupByMethods;
  }

  public void setGroupByMethods(boolean groupByMethods) {
    if (myGroupByMethods != groupByMethods) {
      myGroupByMethods = groupByMethods;
      rebuildTree();
    }
  }

  public boolean isGroupByClasses() {
    return myGroupByClasses;
  }

  public void setGroupByClasses(boolean groupByClasses) {
    if (myGroupByClasses != groupByClasses) {
      myGroupByClasses = groupByClasses;
      rebuildTree();
    }
  }

  public boolean isFlattenPackages() {
    return myFlattenPackages;
  }

  public void setFlattenPackages(boolean flattenPackages) {
    if (myFlattenPackages != flattenPackages) {
      myFlattenPackages = flattenPackages;
      rebuildTree();
    }
  }

  protected void checkNode(final CheckedTreeNode node, final boolean checked) {
    TreeUtil.traverseDepth(node, new TreeUtil.Traverse() {
      public boolean accept(Object _node) {
        CheckedTreeNode node = (CheckedTreeNode)_node;
        doCheckNode(node, checked);
        return true;
      }
    });
    boolean parentChecked = checked;
    for (CheckedTreeNode parent = (CheckedTreeNode)node.getParent(); parent != null; parent = (CheckedTreeNode)parent.getParent()) {
      if (!parentChecked) {
        final int childCount = parent.getChildCount();
        for (int idx = 0; idx < childCount && !parentChecked; idx++) {
          parentChecked = ((CheckedTreeNode)parent.getChildAt(idx)).isChecked();
        }
      }
      if (parentChecked == parent.isChecked()) {
        break;
      }
      doCheckNode(parent, parentChecked);
    }
  }

  private void doCheckNode(CheckedTreeNode node, boolean parentChecked) {
    super.checkNode(node, parentChecked);
    final Object descriptor = node.getUserObject();
    if (descriptor instanceof BreakpointDescriptor) {
      final Breakpoint breakpoint = ((BreakpointDescriptor)descriptor).getBreakpoint();
      breakpoint.ENABLED = parentChecked;
      breakpoint.updateUI();
    }
  }

  public void addBreakpoint(final Breakpoint breakpoint) {
    myBreakpoints.add(breakpoint);
    breakpoint.updateUI(new Runnable() {
      public void run() {
        rebuildTree();
        selectBreakpoint(breakpoint);
      }
    });
  }

  public void removeBreakpoint(Breakpoint breakpoint) {
    myBreakpoints.remove(breakpoint);
    rebuildTree();
  }

  public void removeBreakpoints(Breakpoint[] breakpoints) {
    myBreakpoints.removeAll(Arrays.asList(breakpoints));
    rebuildTree();
  }

  public void setBreakpoints(Breakpoint[] breakpoints) {
    myBreakpoints.clear();
    myBreakpoints.addAll(Arrays.asList(breakpoints));
    rebuildTree();
  }
  
  private void rebuildTree() {
    final TreeStateSnapshot treeStateSnapshot = new TreeStateSnapshot(this);
    myRootNode.removeAllChildren();
    myDescriptorToNodeMap.clear();
    myDescriptorToNodeMap.put((TreeDescriptor)myRootNode.getUserObject(), myRootNode);
    // build tree
    for (final Breakpoint breakpoint : myBreakpoints) {
      CheckedTreeNode node = createNode(new BreakpointDescriptor(breakpoint));
      node.setChecked(breakpoint.ENABLED);
      addNode(node);
    }
    // remove all package nodes with one child
    final int count = myRootNode.getChildCount();
    final List<CheckedTreeNode> children = new ArrayList<CheckedTreeNode>();
    for (int idx = 0; idx < count; idx++) {
      CheckedTreeNode child = (CheckedTreeNode)myRootNode.getChildAt(idx);
      if (!(child.getUserObject() instanceof PackageDescriptor)) {
        children.add(child);
        continue;
      }
      while (child.getUserObject() instanceof PackageDescriptor && child.getChildCount() <= 1) {
        child = (CheckedTreeNode)child.getChildAt(0);
      }
      if (!(child.getUserObject() instanceof PackageDescriptor)) {
        child = (CheckedTreeNode)child.getParent();
      }
      for (CheckedTreeNode childToRemove = (CheckedTreeNode)child.getParent(); !childToRemove.equals(myRootNode); childToRemove = (CheckedTreeNode)childToRemove.getParent()) {
        myDescriptorToNodeMap.remove(childToRemove.getUserObject());
      }
      children.add(child);
    }
    for (final CheckedTreeNode aChildren : children) {
      aChildren.removeFromParent();
    }
    myRootNode.removeAllChildren();
    for (final CheckedTreeNode child : children) {
      myRootNode.add(child);
    }

    ((DefaultTreeModel)getModel()).nodeStructureChanged(myRootNode);
    treeStateSnapshot.restore(this);
    expandPath(new TreePath(myRootNode));
  }
  
  private @NotNull CheckedTreeNode  createNode(final TreeDescriptor descriptor) {
    final CheckedTreeNode node = new CheckedTreeNode(descriptor);
    myDescriptorToNodeMap.put(descriptor, node);
    return node;
  }

  /**
   * @param node
   */
  private void addNode(CheckedTreeNode node) {
    for (final NodeAppender appender : myAppenders) {
      node = appender.append(node);
      if (node == null) {
        break;
      }
    }
    if (node != null) {
      attachNodeToParent(getDescriptor(myRootNode), node);
    }
  }

  private static TreeDescriptor getDescriptor(final CheckedTreeNode node) {
    return (TreeDescriptor)node.getUserObject();
  }

  /**
   * @param parentDescriptor a descriptor of the childNode (possibly not existing) to attach to
   * @param childNode the childNode to be attached 
   * @return either parent node if it has just been created or null, if the node has been attached to already existing childNode
   */
  private CheckedTreeNode attachNodeToParent(final TreeDescriptor parentDescriptor, final CheckedTreeNode childNode) {
    CheckedTreeNode parentNode = myDescriptorToNodeMap.get(parentDescriptor);
    try {
      if (parentNode != null) {
        final boolean parentChecked = parentNode.isChecked() || childNode.isChecked();
        final boolean checkedValueChanged = parentNode.isChecked() != parentChecked;
        parentNode.setChecked(parentChecked);
        parentNode.add(childNode);
        if (checkedValueChanged && parentChecked) {
          for (CheckedTreeNode parent = (CheckedTreeNode)parentNode.getParent(); parent != null; parent = (CheckedTreeNode)parent.getParent()) {
            parent.setChecked(true);
          }
        }
        return null;  // added to already existing, so stop iteration over appenders
      }
      parentNode = createNode(parentDescriptor);
      parentNode.setChecked(childNode.isChecked());
      parentNode.add(childNode);
      return parentNode;
    }
    finally {
      if (parentNode != null && parentNode.getChildCount() == 1) {
        expandPath(new TreePath(parentNode.getPath()));
      }
    }
  }

  private static class BreakpointTreeCellRenderer extends CheckboxTreeCellRenderer {
    public void customizeCellRenderer(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
      final CheckedTreeNode node = (CheckedTreeNode)value;
      final TreeDescriptor descriptor = getDescriptor(node);
      descriptor.customizeCellRenderer(getTextRenderer(), node, selected, node.isChecked(), expanded, leaf, hasFocus);
    }
  }

  private abstract class NodeAppender {
    public abstract CheckedTreeNode append(CheckedTreeNode node);
  }

  private class BreakpointToMethodAppender extends NodeAppender {
    public CheckedTreeNode append(CheckedTreeNode node) {
      if (!myGroupByMethods) {
        return node;
      }
      final TreeDescriptor descriptor = getDescriptor(node);
      if (!(descriptor instanceof BreakpointDescriptor)) {
        return node;
      }
      final Breakpoint breakpoint = ((BreakpointDescriptor)descriptor).getBreakpoint();
      if (!(breakpoint instanceof LineBreakpoint)) {
        return node;
      }
      final LineBreakpoint lineBreakpoint = (LineBreakpoint)breakpoint;
      final String methodName = lineBreakpoint.getMethodName();
      final String className = lineBreakpoint.getClassName();
      if (methodName == null || className == null) {
        return node;
      }
      return attachNodeToParent(new MethodDescriptor(methodName, className), node);
    }
  }

  private class BreakpointToClassAppender extends NodeAppender {
    public CheckedTreeNode append(CheckedTreeNode node) {
      if (!myGroupByClasses) {
        return node;
      }
      final TreeDescriptor descriptor = getDescriptor(node);
      if (!(descriptor instanceof BreakpointDescriptor)) {
        return node;
      }
      
      final Breakpoint breakpoint = ((BreakpointDescriptor)descriptor).getBreakpoint();
      final String className;
      if (breakpoint instanceof ExceptionBreakpoint) {
        className = ((ExceptionBreakpoint)breakpoint).getQualifiedName();
      }
      else if (breakpoint instanceof BreakpointWithHighlighter) {
        className = ((BreakpointWithHighlighter)breakpoint).getClassName();
      }
      else {
        className = null;
      }
      if (className == null) {
        return node;
      }
      return attachNodeToParent(new ClassDescriptor(className), node);
    }
  }

  private class BreakpointToPackageAppender extends NodeAppender {
    public CheckedTreeNode append(CheckedTreeNode node) {
      final TreeDescriptor descriptor = getDescriptor(node);
      if (!(descriptor instanceof BreakpointDescriptor)) {
        return node;
      }
      
      final Breakpoint breakpoint = ((BreakpointDescriptor)descriptor).getBreakpoint();
      final String className;
      if (breakpoint instanceof ExceptionBreakpoint) {
        className = ((ExceptionBreakpoint)breakpoint).getQualifiedName();
      }
      else if (breakpoint instanceof BreakpointWithHighlighter) {
        className = ((BreakpointWithHighlighter)breakpoint).getClassName();
      }
      else {
        className = null;
      }
      if (className == null) {
        return node;
      }
      final String packageName = new ClassDescriptor(className).getPackageName();
      return attachNodeToParent(new PackageDescriptor(packageName), node);
    }
  }

  private class MethodToClassAppender extends NodeAppender {
    public CheckedTreeNode append(CheckedTreeNode node) {
      if (!myGroupByClasses) {
        return node;
      }
      final TreeDescriptor descriptor = getDescriptor(node);
      if (!(descriptor instanceof MethodDescriptor)) {
        return node;
      }
      final String className = ((MethodDescriptor)descriptor).getClassName();
      return attachNodeToParent(new ClassDescriptor(className), node);
    }
  }

  private class MethodToPackageAppender extends NodeAppender {
    public CheckedTreeNode append(CheckedTreeNode node) {
      final TreeDescriptor descriptor = getDescriptor(node);
      if (!(descriptor instanceof MethodDescriptor)) {
        return node;
      }
      final String className = ((MethodDescriptor)descriptor).getClassName();
      final String packageName = new ClassDescriptor(className).getPackageName();
      return attachNodeToParent(new PackageDescriptor(packageName), node);
    }
  }

  private class ClassToPackageAppender extends NodeAppender {
    public CheckedTreeNode append(CheckedTreeNode node) {
      final TreeDescriptor descriptor = getDescriptor(node);
      if (!(descriptor instanceof ClassDescriptor)) {
        return node;
      }

      final String packageName = ((ClassDescriptor)descriptor).getPackageName();
      return attachNodeToParent(new PackageDescriptor(packageName), node);
    }
  }

  private class PackageToPackageAppender extends NodeAppender {
    public CheckedTreeNode append(CheckedTreeNode node) {
      if (myFlattenPackages) {
        return node;
      }
      final TreeDescriptor descriptor = getDescriptor(node);
      if (!(descriptor instanceof PackageDescriptor)) {
        return node;
      }

      final PackageDescriptor packageDescriptor = (PackageDescriptor)descriptor;
      final String parentPackageName = packageDescriptor.getParentPackageName();
      if (parentPackageName == null) {
        return node;
      }
      final CheckedTreeNode parentNode = attachNodeToParent(new PackageDescriptor(parentPackageName), node);
      if (parentNode == null) {
        return null;
      }
      return append(parentNode);
    }
  }

  private static class RootDescriptor extends TreeDescriptor {

    protected String getDisplayString() {
      return "";
    }

    protected Icon getDisplayIcon() {
      return Icons.PROJECT_ICON;
    }
  }
  
  private static class TreeStateSnapshot {
    private final Object[] myExpandedUserObjects;
    private final Object[] mySelectedUserObjects;
    private static final Object[][] EMPTY = new Object[0][];

    public TreeStateSnapshot(BreakpointTree tree) {
      final List<TreePath> expandedPaths = TreeUtil.collectExpandedPaths(tree);
      myExpandedUserObjects = getUserObjects(expandedPaths.toArray(new TreePath[expandedPaths.size()]));
      mySelectedUserObjects =getUserObjects(tree.getSelectionPaths());
    }

    private Object[] getUserObjects(final TreePath[] treePaths) {
      if (treePaths == null) {
        return EMPTY;
      }
      Object[] userObjects = new Object[treePaths.length];
      int index = 0;
      for (TreePath path : treePaths) {
        userObjects[index++] = ((CheckedTreeNode)path.getLastPathComponent()).getUserObject();
      }
      return userObjects;
    }

    public void restore(BreakpointTree tree) {
      final List<TreePath> pathsToExpand = getPaths(tree, myExpandedUserObjects);
      if (pathsToExpand.size() > 0) {
        TreeUtil.restoreExpandedPaths(tree, pathsToExpand);
      }

      final List<TreePath> pathsToSelect = getPaths(tree, mySelectedUserObjects);
      if (pathsToSelect.size() > 0) {
        tree.getSelectionModel().clearSelection();
        /*
        for (TreePath path : pathsToSelect) {
          TreeUtil.selectPath(tree, path.getParentPath());
        }
        */
        tree.setSelectionPaths(pathsToSelect.toArray(new TreePath[pathsToSelect.size()]));
      }
    }

    private List<TreePath> getPaths(BreakpointTree tree, final Object[] userObjects) {
      final List<TreePath> paths = new ArrayList<TreePath>(userObjects.length);
      for (Object descriptor : userObjects) {
        final CheckedTreeNode node = tree.myDescriptorToNodeMap.get(descriptor);
        if (node != null) {
          paths.add(new TreePath(node.getPath()));
        }
      }
      return paths;
    }
  }
}
