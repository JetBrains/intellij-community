package com.intellij.ide.projectView.impl;

import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.SelectableTreeStructureProvider;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.ClassTreeNode;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;

public class ClassesTreeStructureProvider implements SelectableTreeStructureProvider {
  private final Project myProject;

  public ClassesTreeStructureProvider(Project project) {
    myProject = project;
  }

  public Collection<AbstractTreeNode> modify(AbstractTreeNode parent, Collection<AbstractTreeNode> children, ViewSettings settings) {
    ArrayList<AbstractTreeNode> result = new ArrayList<AbstractTreeNode>();
    for (final AbstractTreeNode child : children) {
      Object o = child.getValue();
      if (o instanceof PsiJavaFile) {
        PsiJavaFile psiJavaFile = (PsiJavaFile)o;
        final VirtualFile virtualFile = psiJavaFile.getVirtualFile();
        if (virtualFile.getFileType() == StdFileTypes.JAVA || virtualFile.getFileType() == StdFileTypes.CLASS) {
          PsiClass[] classes = psiJavaFile.getClasses();
          if (classes.length != 0) {
            for (PsiClass aClass : classes) {
              if (aClass.isValid()) {
                result.add(new ClassTreeNode(myProject, aClass, ((ProjectViewNode)parent).getSettings()));
              }
            }
            continue;
          }
        }
      }
      result.add(child);
    }
    return result;
  }

  public Object getData(Collection<AbstractTreeNode> selected, String dataName) {
    return null;
  }

  public PsiElement getTopLevelElement(final PsiElement element) {
    PsiFile baseRootFile = getBaseRootFile(element);
    if (baseRootFile == null) return null;
    PsiElement current = element;
    while (current != null) {
      if (current instanceof PsiFileSystemItem) {
        break;
      }
      if (isTopLevelClass(current, baseRootFile)) {
        break;
      }
      current = current.getParent();
    }

    if (current instanceof PsiClassOwner) {
      PsiClass[] classes = ((PsiClassOwner)current).getClasses();
      if (classes.length > 0 && isTopLevelClass(classes[0], baseRootFile)) {
        current = classes[0];
      }
    }
    return current instanceof PsiClass ? current : baseRootFile;
  }

  @Nullable
  private static PsiFile getBaseRootFile(PsiElement element) {
    final PsiFile containingFile = element.getContainingFile();
    if (containingFile == null) return null;

    final FileViewProvider viewProvider = containingFile.getViewProvider();
    return viewProvider.getPsi(viewProvider.getBaseLanguage());
  }

  private static boolean isTopLevelClass(final PsiElement element, PsiFile baseRootFile) {

    if (!(element instanceof PsiClass)) {
      return false;
    }
    final PsiElement parent = element.getParent();
                                        // do not select JspClass
    return parent instanceof PsiFile && parent.getLanguage() == baseRootFile.getLanguage();
  }
}
