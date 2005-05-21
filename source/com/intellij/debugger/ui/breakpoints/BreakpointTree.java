/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.debugger.ui.breakpoints;import com.intellij.ui.CheckboxTree;
import com.intellij.ui.CheckedTreeNode;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Eugene Zhuravlev
 *         Date: May 20, 2005
 */
public class BreakpointTree extends CheckboxTree {
  private final CheckedTreeNode myRootNode;
  private final List<Breakpoint> myBreakpoints = new ArrayList<Breakpoint>();
  private final Map<TreeDescriptor, CheckedTreeNode> myDescriptorToNodeMap = new HashMap<TreeDescriptor, CheckedTreeNode>();
  private final NodeAppender[] myAppenders = new NodeAppender[] {
    new BreakpointToMethodAppender(),
    new MethodToClassAppender(),
    new ClassToPackageAppender(),
    new PackageToPackageAppender()
  };

  private interface TreeDescriptor {
    public void customizeCellRenderer(final ColoredTreeCellRenderer targetRenderer,
                                      boolean selected, final boolean checked, boolean expanded, boolean leaf, int row, boolean hasFocus);
  }

  private static final class BreakpointDescriptor implements TreeDescriptor {
    private final Breakpoint myBreakpoint;
    public BreakpointDescriptor(Breakpoint breakpoint) {
      myBreakpoint = breakpoint;
    }
    public @NotNull Breakpoint getBreakpoint() {
      return myBreakpoint;
    }

    public void customizeCellRenderer(final ColoredTreeCellRenderer targetRenderer, boolean selected,
                                      final boolean checked, boolean expanded, boolean leaf, int row, boolean hasFocus) {
      final Icon icon = (myBreakpoint instanceof BreakpointWithHighlighter)?
                        myBreakpoint.ENABLED? ((BreakpointWithHighlighter)myBreakpoint).getSetIcon() : ((BreakpointWithHighlighter)myBreakpoint).getDisabledIcon() :
                        myBreakpoint.getIcon();
      targetRenderer.setIcon(icon);
      targetRenderer.append(myBreakpoint.getDisplayName(), checked? SimpleTextAttributes.SIMPLE_CELL_ATTRIBUTES : SimpleTextAttributes.GRAYED_ATTRIBUTES);
    }

    public boolean equals(final Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      final BreakpointDescriptor breakpointDescriptor = (BreakpointDescriptor)o;

      if (!myBreakpoint.equals(breakpointDescriptor.myBreakpoint)) return false;

      return true;
    }

    public int hashCode() {
      return myBreakpoint.hashCode();
    }
  }

  private static final class MethodDescriptor implements TreeDescriptor {
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

    public void customizeCellRenderer(final ColoredTreeCellRenderer targetRenderer, boolean selected,
                                      final boolean checked, boolean expanded, boolean leaf, int row, boolean hasFocus) {
      final Icon icon = null; // todo
      targetRenderer.setIcon(icon);
      targetRenderer.append(myMethodName, checked? SimpleTextAttributes.SIMPLE_CELL_ATTRIBUTES : SimpleTextAttributes.GRAYED_ATTRIBUTES);
    }

    public boolean equals(final Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      final MethodDescriptor methodDescriptor = (MethodDescriptor)o;

      if (!myClassName.equals(methodDescriptor.myClassName)) return false;
      if (!myMethodName.equals(methodDescriptor.myMethodName)) return false;

      return true;
    }

    public int hashCode() {
      int result;
      result = myClassName.hashCode();
      result = 29 * result + myMethodName.hashCode();
      return result;
    }
  }

  private static final class ClassDescriptor implements TreeDescriptor {
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

    public void customizeCellRenderer(final ColoredTreeCellRenderer targetRenderer, boolean selected,
                                      final boolean checked, boolean expanded, boolean leaf, int row, boolean hasFocus) {
      final Icon icon = null; // todo
      targetRenderer.setIcon(icon);
      targetRenderer.append(myClassName, checked? SimpleTextAttributes.SIMPLE_CELL_ATTRIBUTES : SimpleTextAttributes.GRAYED_ATTRIBUTES);
    }

    public boolean equals(final Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      final ClassDescriptor classDescriptor = (ClassDescriptor)o;

      if (!myClassName.equals(classDescriptor.myClassName)) return false;

      return true;
    }

    public int hashCode() {
      return myClassName.hashCode();
    }
  }

  private static final class PackageDescriptor implements TreeDescriptor {
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

    public void customizeCellRenderer(final ColoredTreeCellRenderer targetRenderer, boolean selected,
                                      final boolean checked, boolean expanded, boolean leaf, int row, boolean hasFocus) {
      final Icon icon = null; // todo
      targetRenderer.setIcon(icon);
      targetRenderer.append(myPackageName, checked? SimpleTextAttributes.SIMPLE_CELL_ATTRIBUTES : SimpleTextAttributes.GRAYED_ATTRIBUTES);
    }

    public boolean equals(final Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      final PackageDescriptor packageDescriptor = (PackageDescriptor)o;

      if (!myPackageName.equals(packageDescriptor.myPackageName)) return false;

      return true;
    }

    public int hashCode() {
      return myPackageName.hashCode();
    }
  }

  public BreakpointTree() {
    super(new BreakpointTreeCellRenderer(), new CheckedTreeNode(new RootDescriptor()));
    myRootNode = (CheckedTreeNode)getModel().getRoot();
  }

  public void addBreakpoint(Breakpoint breakpoint) {
    myBreakpoints.add(breakpoint);
    CheckedTreeNode node = createNode(new BreakpointDescriptor(breakpoint));
    addNode(node);
  }

  private CheckedTreeNode createNode(final TreeDescriptor descriptor) {
    final CheckedTreeNode node = new CheckedTreeNode(descriptor);
    myDescriptorToNodeMap.put(descriptor, node);
    return node;
  }

  /**
   * @param node
   */
  private void addNode(CheckedTreeNode node) {
    for (int idx = 0; idx < myAppenders.length; idx++) {
      final NodeAppender appender = myAppenders[idx];
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
    CheckedTreeNode methodNode = myDescriptorToNodeMap.get(parentDescriptor);
    if (methodNode != null) {
      methodNode.add(childNode);
      return null;  // added to already existing, so stop iteration over appenders
    }
    methodNode = createNode(parentDescriptor);
    methodNode.add(childNode);
    return methodNode;
  }

  private static class BreakpointTreeCellRenderer extends CheckboxTreeCellRenderer {
    public void customizeCellRenderer(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
      final CheckedTreeNode node = (CheckedTreeNode)value;
      final TreeDescriptor descriptor = getDescriptor(node);
      descriptor.customizeCellRenderer(getTextRenderer(), selected, node.isChecked(), expanded, leaf, row, hasFocus);
    }
  }

  private abstract class NodeAppender {
    public abstract CheckedTreeNode append(CheckedTreeNode node);
  }

  private class BreakpointToMethodAppender extends NodeAppender {
    public CheckedTreeNode append(CheckedTreeNode node) {
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

  private class MethodToClassAppender extends NodeAppender {
    public CheckedTreeNode append(CheckedTreeNode node) {
      final TreeDescriptor descriptor = getDescriptor(node);
      if (!(descriptor instanceof MethodDescriptor)) {
        return node;
      }
      final String className = ((MethodDescriptor)descriptor).getClassName();
      return attachNodeToParent(new ClassDescriptor(className), node);
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
      final TreeDescriptor descriptor = getDescriptor(node);
      if (!(descriptor instanceof PackageDescriptor)) {
        return node;
      }

      final PackageDescriptor packageDescriptor = (PackageDescriptor)descriptor;
      final String parentPackageName = packageDescriptor.getParentPackageName();
      if (parentPackageName == null) {
        return node;
      }
      return attachNodeToParent(new PackageDescriptor(parentPackageName), node);
    }
  }

  private static class RootDescriptor implements TreeDescriptor {
    public void customizeCellRenderer(final ColoredTreeCellRenderer targetRenderer,
                                      boolean selected,
                                      final boolean checked,
                                      boolean expanded,
                                      boolean leaf,
                                      int row,
                                      boolean hasFocus) {
    }
  }
}
