package com.intellij.xdebugger.impl.breakpoints.ui;

import com.intellij.ui.CheckboxTree;
import com.intellij.ui.CheckedTreeNode;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ui.tree.TreeUtil;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.XBreakpointType;
import com.intellij.xdebugger.impl.breakpoints.XBreakpointUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.util.*;

/**
 * @author nik
 */
public class XBreakpointsTree<B extends XBreakpoint<?>> extends CheckboxTree {
  private final CheckedTreeNode myRoot;
  private Map<B, BreakpointNode<B>> myNodes = new HashMap<B, BreakpointNode<B>>();

  private XBreakpointsTree(final CheckedTreeNode root) {
    super(new BreakpointsTreeCellRenderer(), root);
    myRoot = root;
    setShowsRootHandles(false);
  }

  public static <B extends XBreakpoint<?>> XBreakpointsTree<B> createTree() {
    return new XBreakpointsTree<B>(new CheckedTreeNode(null));
  }

  public void buildTree(@NotNull Collection<? extends B> breakpoints) {
    myRoot.removeAllChildren();
    myNodes.clear();
    for (B breakpoint : breakpoints) {
      BreakpointNode<B> node = new BreakpointNode<B>(breakpoint);
      myRoot.add(node);
      myNodes.put(breakpoint, node);
    }
    ((DefaultTreeModel)getModel()).nodeStructureChanged(myRoot);
    expandPath(new TreePath(myRoot));
  }

  protected void checkNode(final CheckedTreeNode node, final boolean checked) {
    if (node instanceof BreakpointNode) {
      ((BreakpointNode)node).getBreakpoint().setEnabled(checked);
    }
    super.checkNode(node, checked);
  }

  public List<B> getSelectedBreakpoints() {
    final ArrayList<B> list = new ArrayList<B>();
    TreePath[] selectionPaths = getSelectionPaths();
    if (selectionPaths == null || selectionPaths.length == 0) return list;

    for (TreePath selectionPath : selectionPaths) {
      TreeUtil.traverseDepth((TreeNode)selectionPath.getLastPathComponent(), new TreeUtil.Traverse() {
        public boolean accept(final Object node) {
          if (node instanceof BreakpointNode) {
            list.add(((BreakpointNode<B>)node).getBreakpoint());
          }
          return true;
        }
      });
    }

    return list;
  }

  public void selectBreakpoint(final B breakpoint) {
    BreakpointNode<B> node = myNodes.get(breakpoint);
    if (node != null) {
      TreeUtil.selectNode(this, node);
    }
  }

  private static class BreakpointsTreeCellRenderer extends CheckboxTreeCellRenderer {
    public void customizeCellRenderer(final JTree tree,
                                        final Object value,
                                        final boolean selected,
                                        final boolean expanded, final boolean leaf, final int row, final boolean hasFocus) {
      if (value instanceof BreakpointNode) {
        BreakpointNode node = (BreakpointNode)value;
        XBreakpoint breakpoint = node.getBreakpoint();
        String text = XBreakpointUtil.getDisplayText(breakpoint);
        getTextRenderer().setIcon(node.getIcon());
        getTextRenderer().append(text, node.getTextAttributes());
      }
    }

  }

  private static class BreakpointNode<B extends XBreakpoint<?>> extends CheckedTreeNode {
    private B myBreakpoint;

    private BreakpointNode(final B breakpoint) {
      super(null);
      myBreakpoint = breakpoint;
      setChecked(breakpoint.isEnabled());
    }

    public B getBreakpoint() {
      return myBreakpoint;
    }

    public Icon getIcon() {
      XBreakpointType type = myBreakpoint.getType();
      return isChecked() ? type.getEnabledIcon() : type.getDisabledIcon();
    }

    public SimpleTextAttributes getTextAttributes() {
      return isChecked() ? SimpleTextAttributes.SIMPLE_CELL_ATTRIBUTES : SimpleTextAttributes.GRAYED_ATTRIBUTES;
    }
  }
}
