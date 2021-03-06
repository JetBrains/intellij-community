// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.utils;

import com.intellij.ide.util.ElementsChooser;
import com.intellij.openapi.externalSystem.action.ExternalSystemActionUtil;
import com.intellij.ui.treeStructure.SimpleTree;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.*;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;

public final class MavenUIUtil {
  public static void executeAction(final String actionId, final InputEvent e) {
    executeAction(actionId, "", e);
  }

  public static void executeAction(final String actionId, final String place, final InputEvent e) {
    ExternalSystemActionUtil.executeAction(actionId, place, e);
  }

  public static <E> void setElements(ElementsChooser<E> chooser, Collection<? extends E> all, Collection<? extends E> selected, Comparator<? super E> comparator) {
    List<E> selection = chooser.getSelectedElements();
    chooser.clear();
    Collection<E> sorted = new TreeSet<>(comparator);
    sorted.addAll(all);
    for (E element : sorted) {
      chooser.addElement(element, selected.contains(element));
    }
    chooser.selectElements(selection);
  }

  public static void installCheckboxRenderer(final SimpleTree tree, final CheckboxHandler handler) {
    final JCheckBox checkbox = new JCheckBox();

    final JPanel panel = new JPanel(new BorderLayout());
    panel.add(checkbox, BorderLayout.WEST);

    final TreeCellRenderer baseRenderer = tree.getCellRenderer();
    tree.setCellRenderer(new TreeCellRenderer() {
      @Override
      public Component getTreeCellRendererComponent(final JTree tree,
                                                    final Object value,
                                                    final boolean selected,
                                                    final boolean expanded,
                                                    final boolean leaf,
                                                    final int row,
                                                    final boolean hasFocus) {
        final Component baseComponent = baseRenderer.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);

        final Object userObject = ((DefaultMutableTreeNode)value).getUserObject();
        if (!handler.isVisible(userObject)) {
          return baseComponent;
        }

        Color foreground = UIUtil.getTreeForeground(selected, hasFocus);
        Color background = UIUtil.getTreeBackground(selected, hasFocus);

        panel.add(baseComponent, BorderLayout.CENTER);
        panel.setBackground(background);
        panel.setForeground(foreground);

        CheckBoxState state = handler.getState(userObject);
        checkbox.setSelected(state != CheckBoxState.UNCHECKED);
        checkbox.setEnabled(state != CheckBoxState.PARTIAL);
        checkbox.setBackground(background);
        checkbox.setForeground(foreground);

        return panel;
      }
    });

    tree.addMouseListener(new MouseAdapter() {
      @Override
      public void mousePressed(MouseEvent e) {
        int row = tree.getRowForLocation(e.getX(), e.getY());
        if (row >= 0) {
          TreePath path = tree.getPathForRow(row);
          if (!isCheckboxEnabledFor(path, handler)) return;
          Rectangle checkBounds = checkbox.getBounds();
          checkBounds.setLocation(tree.getRowBounds(row).getLocation());
          if (checkBounds.contains(e.getPoint())) {
            tree.setSelectionRow(row);
            handler.toggle(path, e);
            e.consume();
          }
        }
      }
    });

    tree.addKeyListener(new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_SPACE) {
          TreePath[] treePaths = tree.getSelectionPaths();
          if (treePaths != null) {
            for (TreePath treePath : treePaths) {
              if (!isCheckboxEnabledFor(treePath, handler)) continue;
              handler.toggle(treePath, e);
            }
            e.consume();
          }
        }
      }
    });
  }

  private static boolean isCheckboxEnabledFor(TreePath path, CheckboxHandler handler) {
    Object userObject = ((DefaultMutableTreeNode)path.getLastPathComponent()).getUserObject();
    return handler.isVisible(userObject);
  }

  public interface CheckboxHandler {
    void toggle(TreePath treePath, final InputEvent e);

    boolean isVisible(Object userObject);

    CheckBoxState getState(Object userObject);
  }

  public enum CheckBoxState {
    CHECKED, UNCHECKED, PARTIAL
  }
}
