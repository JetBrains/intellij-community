package com.intellij.packageDependencies.ui;

import javax.swing.*;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.TreePath;
import java.util.*;

public class TreeExpantionMonitor {
  public static TreeExpantionMonitor install(JTree tree) {
    return new TreeExpantionMonitor(tree);
  }

  private Set<TreePath> myExpandedPaths = new HashSet<TreePath>();
  private List<TreePath> mySelectionPath = new ArrayList<TreePath>();
  private JTree myTree;
  private boolean myFrozen = false;


  private TreeExpantionMonitor(JTree tree) {
    myTree = tree;
    myTree.getSelectionModel().addTreeSelectionListener(new TreeSelectionListener() {
      public void valueChanged(TreeSelectionEvent e) {
        if (myFrozen) return;
        mySelectionPath = new ArrayList<TreePath>();
        TreePath[] paths = myTree.getSelectionPaths();
        if (paths != null) {
          for (int i = 0; i < paths.length; i++) {
            mySelectionPath.add(paths[i]);
          }
        }
      }
    });

    myTree.addTreeExpansionListener(new TreeExpansionListener() {
      public void treeExpanded(TreeExpansionEvent event) {
        if (myFrozen) return;
        TreePath path = event.getPath();
        if (path != null) {
          myExpandedPaths.add(path);
        }
      }

      public void treeCollapsed(TreeExpansionEvent event) {
        if (myFrozen) return;
        TreePath path = event.getPath();
        if (path != null) {
          TreePath[] allPaths = myExpandedPaths.toArray(new TreePath[myExpandedPaths.size()]);
          for (int i = 0; i < allPaths.length; i++) {
            TreePath treePath = allPaths[i];
            if (treePath.equals(path) || path.isDescendant(treePath)) {
              myExpandedPaths.remove(treePath);
            }
          }
        }
      }
    });
  }

  public void freeze() {
    myFrozen = true;
  }

  public void restore() {
    freeze();
    for (int i = 0; i < mySelectionPath.size(); i++) {
      TreePath treePath = mySelectionPath.get(i);
      myTree.getSelectionModel().addSelectionPath(treePath);
    }
    for (Iterator<TreePath> iterator = myExpandedPaths.iterator(); iterator.hasNext();) {
      myTree.expandPath(iterator.next());
    }
    myFrozen = false;
  }
}