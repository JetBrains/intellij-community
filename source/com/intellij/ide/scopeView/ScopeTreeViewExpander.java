package com.intellij.ide.scopeView;

import com.intellij.packageDependencies.ui.DependecyNodeComparator;
import com.intellij.packageDependencies.ui.DirectoryNode;
import com.intellij.packageDependencies.ui.FileNode;
import com.intellij.packageDependencies.ui.PackageDependenciesNode;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.util.ui.tree.TreeUtil;

import javax.swing.*;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeWillExpandListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.ExpandVetoException;
import javax.swing.tree.TreeNode;
import java.util.HashSet;
import java.util.Set;

/**
 * User: anna
 * Date: 30-Jan-2006
 */
public class ScopeTreeViewExpander implements TreeWillExpandListener {

  private JTree myTree;

  public ScopeTreeViewExpander(final JTree tree) {
    myTree = tree;
  }

  public void treeWillExpand(TreeExpansionEvent event) throws ExpandVetoException {
    final DefaultMutableTreeNode node = (DefaultMutableTreeNode)event.getPath().getLastPathComponent();
    if (node instanceof DirectoryNode) {
      Set<ClassNode> classNodes = null;
      for (int i = node.getChildCount() - 1; i >= 0; i--) {
        final TreeNode childNode = node.getChildAt(i);
        if (childNode instanceof FileNode) {
          final FileNode fileNode = (FileNode)childNode;
          final PsiElement file = fileNode.getPsiElement();
          if (file instanceof PsiJavaFile) {
            final PsiClass[] psiClasses = ((PsiJavaFile)file).getClasses();
            if (classNodes == null) {
              classNodes = new HashSet<ClassNode>();
            }
            for (PsiClass psiClass : psiClasses) {
              if (psiClass != null && psiClass.isValid()) {
                classNodes.add(new ClassNode(psiClass));
              }
            }
            node.remove(fileNode);
          }
        }
      }
      if (classNodes != null){
        for (ClassNode classNode : classNodes) {
          node.add(classNode);
        }
      }
      TreeUtil.sort(node, new DependecyNodeComparator());
      ((DefaultTreeModel)myTree.getModel()).reload(node);
    }
  }

  public void treeWillCollapse(TreeExpansionEvent event) throws ExpandVetoException {
    final DefaultMutableTreeNode node = (PackageDependenciesNode)event.getPath().getLastPathComponent();
    if (node instanceof DirectoryNode){
      Set<FileNode> fileNodes = null;
      for (int i = node.getChildCount() - 1; i >= 0; i--) {
        final TreeNode childNode = node.getChildAt(i);
        if (childNode instanceof ClassNode) {
          final ClassNode classNode = (ClassNode)childNode;
          final PsiElement psiElement = classNode.getPsiElement();
          if (psiElement != null && psiElement.isValid()){
            if (fileNodes == null){
              fileNodes = new HashSet<FileNode>();
            }
            fileNodes.add(new FileNode(psiElement.getContainingFile(), true));
          }
          node.remove(classNode);
        }
      }
      if (fileNodes != null){
        for (FileNode fileNode : fileNodes) {
          node.add(fileNode);
        }
      }
      TreeUtil.sort(node, new DependecyNodeComparator());
      ((DefaultTreeModel)myTree.getModel()).reload(node);
    }
  }
}
