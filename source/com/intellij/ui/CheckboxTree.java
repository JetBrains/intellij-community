package com.intellij.ui;

import com.intellij.util.ui.tree.TreeUtil;
import com.intellij.util.ui.Tree;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * User: lex
 * Date: Sep 18, 2003
 * Time: 5:40:20 PM
 */
public class CheckboxTree extends Tree {
  public CheckboxTree(final CheckboxTreeCellRenderer cellRenderer, CheckedTreeNode root) {
    setCellRenderer(cellRenderer);
    setRootVisible(false);
    setShowsRootHandles(true);
    putClientProperty("JTree.lineStyle", "Angled");
    TreeToolTipHandler.install(this);
    TreeUtil.installActions(this);
    new TreeSpeedSearch(this);

    addMouseListener(new MouseAdapter() {
      public void mousePressed(MouseEvent e) {
        int row = getRowForLocation(e.getX(), e.getY());
        if (row >= 0) {
          Rectangle rowBounds = getRowBounds(row);
          cellRenderer.setBounds(rowBounds);
          Rectangle checkBounds = cellRenderer.myCheckbox.getBounds();
          checkBounds.setLocation(rowBounds.getLocation());

          if (checkBounds.contains(e.getPoint())) {
            CheckedTreeNode node = (CheckedTreeNode) getPathForRow(row).getLastPathComponent();
            toggleNode(node);
            e.consume();
            setSelectionRow(row);
          }
        }
      }
    });

    addKeyListener(
      new KeyAdapter() {
        public void keyPressed(KeyEvent e) {
          if(e.getKeyCode() == KeyEvent.VK_SPACE) {
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

  protected boolean toggleNode(CheckedTreeNode node) {
    boolean checked = !node.isChecked();
    checkNode(node, checked);
    return checked;
  }

  protected void checkNode(CheckedTreeNode node, boolean checked) {
    node.setChecked(checked);
    repaint();
  }

  public static abstract class CheckboxTreeCellRenderer extends JPanel implements TreeCellRenderer {
    private final ColoredTreeCellRenderer myTextRenderer;
    public final JCheckBox myCheckbox;

    public CheckboxTreeCellRenderer() {
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
      add(myCheckbox, BorderLayout.WEST);
      add(myTextRenderer, BorderLayout.CENTER);
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
        myCheckbox.setSelected(node.isChecked());

        myCheckbox.setBackground(null);
        setBackground(null);

        myTextRenderer.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
        CheckboxTreeCellRenderer.this.customizeCellRenderer(tree, value, selected, expanded, leaf, row, hasFocus);
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

    public SimpleColoredComponent getTextRenderer    () { return myTextRenderer; }
    public JCheckBox              getCheckbox() { return myCheckbox; }
  }


}
