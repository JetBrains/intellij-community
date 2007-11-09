package com.intellij.packageDependencies.ui;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiManager;

import javax.swing.*;
import javax.swing.tree.TreePath;
import javax.swing.tree.DefaultMutableTreeNode;
import java.util.Enumeration;

public class PackageTreeExpansionMonitor {
  private PackageTreeExpansionMonitor() {
  }

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
}