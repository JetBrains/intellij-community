package com.intellij.ide.projectView.impl;

import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.TreeStructureProvider;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.ClassTreeNode;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiJavaFile;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

public class ClassesTreeStructureProvider implements TreeStructureProvider, ProjectComponent {
  private final Project myProject;

  public ClassesTreeStructureProvider(Project project) {
    myProject = project;
  }

  public Collection<AbstractTreeNode> modify(AbstractTreeNode parent, Collection<AbstractTreeNode> children, ViewSettings settings) {
    ArrayList<AbstractTreeNode> result = new ArrayList<AbstractTreeNode>();
    for (Iterator<AbstractTreeNode> iterator = children.iterator(); iterator.hasNext();) {
      ProjectViewNode treeNode = (ProjectViewNode)iterator.next();
      Object o = treeNode.getValue();
      if (o instanceof PsiJavaFile) {
        PsiJavaFile psiJavaFile = (PsiJavaFile)o;
        PsiClass[] classes = psiJavaFile.getClasses();
        final VirtualFile virtualFile = psiJavaFile.getVirtualFile();
        if (classes.length != 0 && virtualFile.getFileType() == StdFileTypes.JAVA || virtualFile.getFileType() == StdFileTypes.CLASS) {
          for (int i = 0; i < classes.length; i++) {
            PsiClass aClass = classes[i];
            if (aClass.isValid()) {
              result.add(new ClassTreeNode(myProject,aClass, ((ProjectViewNode)parent).getSettings()));
            }
          }
        }
        else {
          result.add(treeNode);
        }
      }
      else {
        result.add(treeNode);
      }
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

  public String getComponentName() {
    return "ClassesTreeStructureProvider";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }
}
