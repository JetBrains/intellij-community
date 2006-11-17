package com.intellij.ide.projectView.impl;

import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.TreeStructureProvider;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.ClassTreeNode;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiJavaFile;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;

public class ClassesTreeStructureProvider implements TreeStructureProvider, ProjectComponent {
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


  public void projectOpened() {
  }

  public void projectClosed() {
  }

  @NotNull
  public String getComponentName() {
    return "ClassesTreeStructureProvider";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }
}
