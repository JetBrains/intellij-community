package com.intellij.packageDependencies.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.PsiManager;
import gnu.trove.Equality;

import javax.swing.*;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.util.*;

public abstract class TreeExpansionMonitor<T> {
  public static TreeExpansionMonitor<PackageDependenciesNode> install(final JTree tree, final Project project) {
    return new TreeExpansionMonitor<PackageDependenciesNode>(tree) {
      protected TreePath findPathByNode(final PackageDependenciesNode node) {
         if (node.getPsiElement() == null){
           return new TreePath(node.getPath());
         }
          PsiManager manager = PsiManager.getInstance(project);
          Enumeration enumeration = ((DefaultMutableTreeNode)tree.getModel().getRoot()).breadthFirstEnumeration();
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
    };
  }

  public static TreeExpansionMonitor<DefaultMutableTreeNode> install(final JTree tree) {
    return install(tree, new Equality<DefaultMutableTreeNode>() {
      public boolean equals(final DefaultMutableTreeNode o1, final DefaultMutableTreeNode o2) {
        return Comparing.equal(o1.getUserObject(), o2.getUserObject());
      }
    });
  }

  public static TreeExpansionMonitor<DefaultMutableTreeNode> install(final JTree tree, final Equality<DefaultMutableTreeNode> equality) {
    return new TreeExpansionMonitor<DefaultMutableTreeNode>(tree) {
      protected TreePath findPathByNode(final DefaultMutableTreeNode node) {
        Enumeration enumeration = ((DefaultMutableTreeNode)tree.getModel().getRoot()).breadthFirstEnumeration();
        while (enumeration.hasMoreElements()) {
          final Object nextElement = enumeration.nextElement();
          if (nextElement instanceof DefaultMutableTreeNode) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode)nextElement;
            if (equality.equals(child, node)) {
              return new TreePath(child.getPath());
            }
          }
        }
        return null;
      }
    };
  }

  private Set<TreePath> myExpandedPaths = new HashSet<TreePath>();
  private List<T> mySelectionNodes = new ArrayList<T>();
  private JTree myTree;
  private boolean myFrozen = false;

  private TreeExpansionMonitor(JTree tree) {
    myTree = tree;
    myTree.getSelectionModel().addTreeSelectionListener(new TreeSelectionListener() {
      public void valueChanged(TreeSelectionEvent e) {
        if (myFrozen) return;
        mySelectionNodes = new ArrayList<T>();
        TreePath[] paths = myTree.getSelectionPaths();
        if (paths != null) {
          for (TreePath path : paths) {
            mySelectionNodes.add((T)path.getLastPathComponent());
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
    for (T mySelectionNode : mySelectionNodes) {
      myTree.getSelectionModel().addSelectionPath(findPathByNode(mySelectionNode));
    }
    for (final TreePath myExpandedPath : myExpandedPaths) {
      myTree.expandPath(myExpandedPath);
    }
    myFrozen = false;
  }

  protected abstract TreePath findPathByNode(final T node);

  public boolean isFreeze() {
    return myFrozen;
  }
}