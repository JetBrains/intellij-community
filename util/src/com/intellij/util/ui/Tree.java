/*
 * Created by IntelliJ IDEA.
 * User: beg
 * Date: Oct 4, 2001
 * Time: 3:33:24 AM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.util.ui;

import com.intellij.Patches;

import javax.swing.*;
import javax.swing.plaf.TreeUI;
import javax.swing.text.Position;
import javax.swing.tree.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.*;

public class Tree extends JTree {

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
  }

  /**
   * Disable Sun's speedsearch
   */
  public TreePath getNextMatch(String prefix, int startingRow, Position.Bias bias) {
    return null;
  }

  private class MyMouseListener extends MouseAdapter {
    public void mousePressed(MouseEvent mouseevent) {
      if(SwingUtilities.isRightMouseButton(mouseevent)) {
        TreePath treepath = getPathForLocation(mouseevent.getX(), mouseevent.getY());
        if (treepath != null) {
          if (getSelectionModel().getSelectionMode() != TreeSelectionModel.SINGLE_TREE_SELECTION) {
            TreePath[] selectionPaths = getSelectionModel().getSelectionPaths();
            if (selectionPaths != null) {
              for (int i = 0; i < selectionPaths.length; i++) {
                if (selectionPaths[i] == treepath) return;
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

  public final void setLineStyleAngled(){
    super.putClientProperty("JTree.lineStyle", "Angled");
  }
}