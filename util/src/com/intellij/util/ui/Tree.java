/*
 * Copyright 2000-2007 JetBrains s.r.o.
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
package com.intellij.util.ui;

import com.intellij.Patches;
import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.plaf.TreeUI;
import javax.swing.text.Position;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.awt.dnd.Autoscroll;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.lang.reflect.Array;
import java.util.ArrayList;

public class Tree extends JTree implements Autoscroll  {

  public Tree() {
    patchTree();
  }

  public Tree(TreeModel treemodel) {
    super(treemodel);
    patchTree();
  }

  public Tree(TreeNode root) {
    super(root);
    patchTree();
  }

  private void patchTree(){
    addMouseListener(new MyMouseListener());
    if(Patches.SUN_BUG_ID_4893787){
      addFocusListener(new MyFocusListener());
    }

    addFocusListener(new SelectionFixer());
  }

  /**
   * Hack to prevent loosing multiple selection on Mac when clicking Ctrl+Left Mouse Button.
   * See faulty code at BasicTreeUI.selectPathForEvent():2245
   * @param e
   */
  protected void processMouseEvent(MouseEvent e) {    
    if (SystemInfo.isMac) {
      if (SwingUtilities.isLeftMouseButton(e) && e.isControlDown() && e.getID() == MouseEvent.MOUSE_PRESSED) {
        int modifiers = (e.getModifiers() & ~(MouseEvent.CTRL_MASK | MouseEvent.BUTTON1_MASK)) |
          MouseEvent.BUTTON3_MASK;
        e = new MouseEvent(e.getComponent(), e.getID(), e.getWhen(), modifiers, e.getX(), e.getY(), e.getClickCount(), true, MouseEvent.BUTTON3);
      }
    }
    super.processMouseEvent(e);
  }

  /**
   * Disable Sun's speedsearch
   */
  public TreePath getNextMatch(String prefix, int startingRow, Position.Bias bias) {
    return null;
  }

  private static final int AUTOSCROLL_MARGIN = 10;

  public Insets getAutoscrollInsets() {
    return new Insets(getLocation().y + AUTOSCROLL_MARGIN, 0, getParent().getHeight() - AUTOSCROLL_MARGIN, getWidth()-1);
  }

  public void autoscroll(Point p) {
    int realrow = getClosestRowForLocation(p.x, p.y);
    if (getLocation().y + p.y <= AUTOSCROLL_MARGIN) {
      if (realrow >= 1) realrow--;
    }
    else {
      if (realrow < getRowCount() - 1) realrow++;
    }
    scrollRowToVisible(realrow);
  }

  private class MyMouseListener extends MouseAdapter {
    public void mousePressed(MouseEvent mouseevent) {
      if(!SwingUtilities.isLeftMouseButton(mouseevent)
         && (SwingUtilities.isRightMouseButton(mouseevent) || SwingUtilities.isMiddleMouseButton(mouseevent))) {
        TreePath treepath = getPathForLocation(mouseevent.getX(), mouseevent.getY());
        if (treepath != null) {
          if (getSelectionModel().getSelectionMode() != TreeSelectionModel.SINGLE_TREE_SELECTION) {
            TreePath[] selectionPaths = getSelectionModel().getSelectionPaths();
            if (selectionPaths != null) {
              for (TreePath selectionPath : selectionPaths) {
                if (selectionPath == treepath) return;
              }
            }
          }
          getSelectionModel().setSelectionPath(treepath);
        }
      }
    }
  }

  /**
   * This is patch for 4893787 SUN bug. The problem is that the BasicTreeUI.FocusHandler repaints
   * only lead selection index on focus changes. It's a problem with multiple selected nodes.
   */
  private class MyFocusListener extends FocusAdapter{
    private void focusChanges(){
      TreePath[] paths = getSelectionPaths();
      if(paths != null){
        TreeUI ui = getUI();
        for(int i = paths.length - 1; i >= 0; i--){
          Rectangle bounds = ui.getPathBounds(Tree.this, paths[i]);
          if(bounds != null){
            repaint(bounds);
          }
        }
      }
    }

    public void focusGained(FocusEvent e) {
      focusChanges();
    }

    public void focusLost(FocusEvent e) {
      focusChanges();
    }
  }

  private class SelectionFixer extends FocusAdapter {
    public void focusGained(final FocusEvent e) {
      final TreePath[] paths = getSelectionPaths();
      if (paths == null || paths.length == 0) {
        for (int eachRow = 0; eachRow < getRowCount(); eachRow++) {
          final TreePath path = getPathForRow(eachRow);
          if (path != null && isVisible(path)) {
            setSelectionPath(path);
            break;
          }
        }
      }
    }
  }

  public final void setLineStyleAngled(){
    UIUtil.setLineStyleAngled(this);
  }

  public <T> T[] getSelectedNodes(Class<T> nodeType, @Nullable NodeFilter<T> filter) {
    TreePath[] paths = getSelectionPaths();
    if (paths == null) return (T[])Array.newInstance(nodeType, 0);

    ArrayList<T> nodes = new ArrayList<T>();
    for (int i = 0; i < paths.length; i++) {
      Object last = paths[i].getLastPathComponent();
      if (nodeType.isAssignableFrom(last.getClass())) {
        if (filter != null && !filter.accept((T)last)) continue;
        nodes.add((T)last);
      }
    }
    T[] result = (T[])Array.newInstance(nodeType, nodes.size());
    nodes.toArray(result);
    return result;
  }

  public static interface NodeFilter<T> {
    boolean accept(T node);
  }
}