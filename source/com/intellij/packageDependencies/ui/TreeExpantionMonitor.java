package com.intellij.packageDependencies.ui;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiManager;

import javax.swing.*;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.util.*;

public class TreeExpantionMonitor {
  public static TreeExpantionMonitor install(JTree tree, Project project) {
    return new TreeExpantionMonitor(tree, project);
  }

  private Set<TreePath> myExpandedPaths = new HashSet<TreePath>();
  private List<PackageDependenciesNode> mySelectionNodes = new ArrayList<PackageDependenciesNode>();
  private JTree myTree;
  private boolean myFrozen = false;
  private Project myProject;

  private TreeExpantionMonitor(JTree tree, Project project) {
    myTree = tree;
    myProject = project;
    myTree.getSelectionModel().addTreeSelectionListener(new TreeSelectionListener() {
      public void valueChanged(TreeSelectionEvent e) {
        if (myFrozen) return;
        mySelectionNodes = new ArrayList<PackageDependenciesNode>();
        TreePath[] paths = myTree.getSelectionPaths();
        if (paths != null) {
          for (int i = 0; i < paths.length; i++) {
            mySelectionNodes.add((PackageDependenciesNode)paths[i].getLastPathComponent());
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
      for (int i = 0; i < mySelectionNodes.size(); i++) {
        myTree.getSelectionModel().addSelectionPath(findPathByNode(mySelectionNodes.get(i)));
      }
      for (Iterator<TreePath> iterator = myExpandedPaths.iterator(); iterator.hasNext();) {
        myTree.expandPath(iterator.next());
      }
      myFrozen = false;
  }


  private TreePath findPathByNode(final PackageDependenciesNode node) {
     if (node.getPsiElement() == null){
       return new TreePath(node.getPath());
     }
      PsiManager manager = PsiManager.getInstance(myProject);
      Enumeration enumeration = ((DefaultMutableTreeNode)myTree.getModel().getRoot()).breadthFirstEnumeration();
      while (enumeration.hasMoreElements()) {
        PackageDependenciesNode child = (PackageDependenciesNode)enumeration.nextElement();
        if (manager.areElementsEquivalent(child.getPsiElement(), node.getPsiElement())) {
          return new TreePath(child.getPath());
        }
      }
      return null;
  }
}