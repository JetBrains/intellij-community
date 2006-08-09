package com.intellij.ide.scopeView;

import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.projectView.PsiClassChildrenSource;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packageDependencies.ui.DependencyNodeComparator;
import com.intellij.packageDependencies.ui.DirectoryNode;
import com.intellij.packageDependencies.ui.FileNode;
import com.intellij.packageDependencies.ui.PackageDependenciesNode;
import com.intellij.psi.*;
import com.intellij.util.ui.tree.TreeUtil;

import javax.swing.*;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeWillExpandListener;
import javax.swing.tree.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * User: anna
 * Date: 30-Jan-2006
 */
public class ScopeTreeViewExpander implements TreeWillExpandListener {

  private JTree myTree;
  private Project myProject;

  public ScopeTreeViewExpander(final JTree tree, final Project project) {
    myTree = tree;
    myProject = project;
  }

  public void treeWillExpand(TreeExpansionEvent event) throws ExpandVetoException {
    ProjectView projectView = ProjectView.getInstance(myProject);
    final TreePath path = myTree.getPathForRow(myTree.getRowForPath(event.getPath()));
    if (path == null) return;
    final DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();
    if (node instanceof DirectoryNode) {
      Set<ClassNode> classNodes = null;
      for (int i = node.getChildCount() - 1; i >= 0; i--) {
        final TreeNode childNode = node.getChildAt(i);
        if (childNode instanceof FileNode) {
          final FileNode fileNode = (FileNode)childNode;
          final PsiElement file = fileNode.getPsiElement();
          if (file instanceof PsiJavaFile) {
            final VirtualFile virtualFile = ((PsiJavaFile)file).getVirtualFile();
            if (virtualFile == null || (virtualFile.getFileType() != StdFileTypes.JAVA && virtualFile.getFileType() != StdFileTypes.CLASS)) {
              return;
            }
            final PsiClass[] psiClasses = ((PsiJavaFile)file).getClasses();
            if (classNodes == null) {
              classNodes = new HashSet<ClassNode>();
            }
            commitDocument((PsiFile)file);
            for (final PsiClass psiClass : psiClasses) {
              if (psiClass != null && psiClass.isValid()) {
                final ClassNode classNode = new ClassNode(psiClass);
                classNodes.add(classNode);
                if (projectView.isShowMembers(ScopeViewPane.ID)) {
                  final List<PsiElement> result = new ArrayList<PsiElement>();
                  PsiClassChildrenSource.DEFAULT_CHILDREN.addChildren(psiClass, result);
                  for (PsiElement psiElement : result) {
                    psiElement.accept(new PsiElementVisitor() {
                      public void visitClass(PsiClass aClass) {
                        classNode.add(new ClassNode(aClass));
                      }

                      public void visitMethod(PsiMethod method) {
                        classNode.add(new MethodNode(method));
                      }

                      public void visitField(PsiField field) {
                        classNode.add(new FieldNode(field));
                      }

                      public void visitReferenceExpression(PsiReferenceExpression expression) {
                      }

                    });
                  }
                }
              }
            }
            node.remove(fileNode);
          }
        }
      }
      if (classNodes != null) {
        for (ClassNode classNode : classNodes) {
          node.add(classNode);
        }
      }
      TreeUtil.sort(node, new DependencyNodeComparator());
      ((DefaultTreeModel)myTree.getModel()).reload(node);
    }
  }

  public void treeWillCollapse(TreeExpansionEvent event) throws ExpandVetoException {
    final TreePath path = myTree.getPathForRow(myTree.getRowForPath(event.getPath()));
    if (path == null) return;
    final DefaultMutableTreeNode node = (PackageDependenciesNode)path.getLastPathComponent();
    if (node instanceof DirectoryNode) {
      Set<FileNode> fileNodes = null;
      for (int i = node.getChildCount() - 1; i >= 0; i--) {
        final TreeNode childNode = node.getChildAt(i);
        if (childNode instanceof ClassNode) {
          final ClassNode classNode = (ClassNode)childNode;
          final PsiElement psiElement = classNode.getPsiElement();
          if (psiElement != null && psiElement.isValid()) {
            if (fileNodes == null) {
              fileNodes = new HashSet<FileNode>();
            }
            fileNodes.add(new FileNode(psiElement.getContainingFile(), true));
          }
          node.remove(classNode);
        }
      }
      if (fileNodes != null) {
        for (FileNode fileNode : fileNodes) {
          node.add(fileNode);
        }
      }
      TreeUtil.sort(node, new DependencyNodeComparator());
      ((DefaultTreeModel)myTree.getModel()).reload(node);
    }
  }

  private void commitDocument(final PsiFile file) {
    final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(myProject);
    final Document document = documentManager.getDocument(file);
    documentManager.commitDocument(document);
  }
}
