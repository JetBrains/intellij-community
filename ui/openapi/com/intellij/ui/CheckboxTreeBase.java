package com.intellij.ui;

import com.intellij.util.ui.Tree;
import com.intellij.util.ui.tree.TreeUtil;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeModel;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Enumeration;

/**
 * @author Dmitry Avdeev
 */
public class CheckboxTreeBase extends Tree {

  public CheckboxTreeBase(final CheckboxTreeCellRendererBase cellRenderer, CheckedTreeNode root) {

    setCellRenderer(cellRenderer);
    setRootVisible(false);
    setShowsRootHandles(true);
    setLineStyleAngled();
    TreeUtil.installActions(this);

    addMouseListener(new MouseAdapter() {
      public void mouseClicked(MouseEvent e) {
        int row = getRowForLocation(e.getX(), e.getY());
        if (row >= 0) {
          Rectangle rowBounds = getRowBounds(row);
          cellRenderer.setBounds(rowBounds);
          Rectangle checkBounds = cellRenderer.myCheckbox.getBounds();
          checkBounds.setLocation(rowBounds.getLocation());

          final CheckedTreeNode node = (CheckedTreeNode) getPathForRow(row).getLastPathComponent();
          if (checkBounds.contains(e.getPoint())) {
            if (node.isEnabled()) {
              toggleNode(node);
              setSelectionRow(row);
            }
            e.consume();
          }
          else if (e.getClickCount() > 1) {
            onDoubleClick(node);
          }
        }
      }
    });

    addKeyListener(
      new KeyAdapter() {
        public void keyPressed(KeyEvent e) {
          if(isToggleEvent(e)) {
            TreePath treePath = getLeadSelectionPath();
            CheckedTreeNode firstNode = (CheckedTreeNode)treePath.getLastPathComponent();
            boolean checked = toggleNode(firstNode);

            TreePath[] selectionPaths = getSelectionPaths();
            for (int i = 0; selectionPaths != null && i < selectionPaths.length; i++) {
              final TreePath selectionPath = selectionPaths[i];
              CheckedTreeNode node = (CheckedTreeNode)selectionPath.getLastPathComponent();
              checkNode(node,checked);
            }

            e.consume();
          }
        }
      }
    );

    setSelectionRow(0);
    setModel(new DefaultTreeModel(root));
  }

  protected void onDoubleClick(final CheckedTreeNode node) {
  }

  protected boolean isToggleEvent(KeyEvent e) {
    return e.getKeyCode() == KeyEvent.VK_SPACE;
  }

  protected boolean toggleNode(CheckedTreeNode node) {
    boolean checked = !node.isChecked();
    checkNode(node, checked);

    // notify model listeners about model change
    final TreeModel model = getModel();
    model.valueForPathChanged(new TreePath(node.getPath()), node.getUserObject());

    return checked;
  }

  public int getToggleClickCount() {
    // to prevent node expanding/collapsing on checkbox toggling
    return -1;
  }

  protected void checkNode(CheckedTreeNode node, boolean checked) {
    node.setChecked(checked);
    repaint();
  }

  protected void adjustParentsAndChildren(final CheckedTreeNode node, final boolean checked) {
    node.setChecked(checked);
    if (checked) {
      CheckedTreeNode parent = (CheckedTreeNode)node.getParent();
      while (parent != null) {
        parent.setChecked(true);
        parent = (CheckedTreeNode)parent.getParent();
      }
    }
    else {
      uncheckChildren(node);
    }
    repaint();
  }

  private static void uncheckChildren(final CheckedTreeNode node) {
    final Enumeration children = node.children();
    while (children.hasMoreElements()) {
      CheckedTreeNode child = (CheckedTreeNode)children.nextElement();
      child.setChecked(false);
      uncheckChildren(child);
    }
  }

  public static abstract class CheckboxTreeCellRendererBase extends JPanel implements TreeCellRenderer {
    private final ColoredTreeCellRenderer myTextRenderer;
    public final JCheckBox myCheckbox;

    public CheckboxTreeCellRendererBase(boolean opaque) {
      super(new BorderLayout());
      myCheckbox = new JCheckBox();
      myTextRenderer = new ColoredTreeCellRenderer() {
        public void customizeCellRenderer(JTree tree,
                                          Object value,
                                          boolean selected,
                                          boolean expanded,
                                          boolean leaf,
                                          int row,
                                          boolean hasFocus) {
        }
      };
      myTextRenderer.setOpaque(opaque);
      add(myCheckbox, BorderLayout.WEST);
      add(myTextRenderer, BorderLayout.CENTER);
    }

    public CheckboxTreeCellRendererBase() {
      this(true);
    }

    public final Component getTreeCellRendererComponent(JTree tree,
                                                  Object value,
                                                  boolean selected,
                                                  boolean expanded,
                                                  boolean leaf,
                                                  int row,
                                                  boolean hasFocus) {
      invalidate();
      if (value instanceof CheckedTreeNode) {
        CheckedTreeNode node = (CheckedTreeNode)value;
        myCheckbox.setEnabled(node.isEnabled());
        myCheckbox.setSelected(node.isChecked());

        myCheckbox.setBackground(null);
        setBackground(null);

        myTextRenderer.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
        customizeCellRenderer(tree, value, selected, expanded, leaf, row, hasFocus);
      }
      return this;
    }

    /**
     * This method is invoked only for customization of component.
     * All component attributes are cleared when this method is being invoked.
     */
    public abstract void customizeCellRenderer(
      JTree tree,
      Object value,
      boolean selected,
      boolean expanded,
      boolean leaf,
      int row,
      boolean hasFocus
    );

    public ColoredTreeCellRenderer getTextRenderer() {
      return myTextRenderer;
    }

    public JCheckBox getCheckbox() {
      return myCheckbox;
    }
  }
}
