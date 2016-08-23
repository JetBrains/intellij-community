/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.maven.utils;

import com.intellij.ide.DataManager;
import com.intellij.ide.util.ElementsChooser;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
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

public class MavenUIUtil {
  public static void executeAction(final String actionId, final InputEvent e) {
    final ActionManager actionManager = ActionManager.getInstance();
    final AnAction action = actionManager.getAction(actionId);
    if (action != null) {
      final Presentation presentation = new Presentation();
      final AnActionEvent event =
        new AnActionEvent(e, DataManager.getInstance().getDataContext(e.getComponent()), "", presentation, actionManager, 0);
      action.update(event);
      if (presentation.isEnabled()) {
        action.actionPerformed(event);
      }
    }
  }

  public static <E> void setElements(ElementsChooser<E> chooser, Collection<E> all, Collection<E> selected, Comparator<E> comparator) {
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

        final Color foreground = selected ? UIUtil.getTreeSelectionForeground() : UIUtil.getTreeTextForeground();

        Color background = selected ? UIUtil.getTreeSelectionBackground(hasFocus) : UIUtil.getTreeTextBackground();

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
      public void mousePressed(MouseEvent e) {
        int row = tree.getRowForLocation(e.getX(), e.getY());
        if (row >= 0) {
          TreePath path = tree.getPathForRow(row);
          if (!isCheckboxEnabledFor(path, handler)) return;

          Rectangle checkBounds = checkbox.getBounds();
          checkBounds.setLocation(tree.getRowBounds(row).getLocation());
          if (checkBounds.contains(e.getPoint())) {
            handler.toggle(path, e);
            e.consume();
            tree.setSelectionRow(row);
          }
        }
      }
    });

    tree.addKeyListener(new KeyAdapter() {
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
