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

public class TreeExpansionMonitor {
  public static TreeExpansionMonitor install(JTree tree, Project project) {
    return new TreeExpansionMonitor(tree, project);
  }

  private Set<TreePath> myExpandedPaths = new HashSet<TreePath>();
  private List<PackageDependenciesNode> mySelectionNodes = new ArrayList<PackageDependenciesNode>();
  private JTree myTree;
  private boolean myFrozen = false;
  private Project myProject;

  private TreeExpansionMonitor(JTree tree, Project project) {
    myTree = tree;
    myProject = project;
    myTree.getSelectionModel().addTreeSelectionListener(new TreeSelectionListener() {
      public void valueChanged(TreeSelectionEvent e) {
        if (myFrozen) return;
        mySelectionNodes = new ArrayList<PackageDependenciesNode>();
        TreePath[] paths = myTree.getSelectionPaths();
        if (paths != null) {
          for (TreePath path : paths) {
            mySelectionNodes.add((PackageDependenciesNode)path.getLastPathComponent());
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
          for (TreePath treePath : allPaths) {
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
    for (PackageDependenciesNode mySelectionNode : mySelectionNodes) {
      myTree.getSelectionModel().addSelectionPath(findPathByNode(mySelectionNode));
    }
    for (final TreePath myExpandedPath : myExpandedPaths) {
      myTree.expandPath(myExpandedPath);
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
        final Object nextElement = enumeration.nextElement();
        if (nextElement instanceof PackageDependenciesNode) { //do not include root
          PackageDependenciesNode child = (PackageDependenciesNode)nextElement;
          if (manager.areElementsEquivalent(child.getPsiElement(), node.getPsiElement())) {
            return new TreePath(child.getPath());
          }
        }
      }
      return null;
  }
}