/**
 * class ViewerTreeStructure
 * created Aug 25, 2001
 * @author Jeka
 */
package com.intellij.internal.psiView;

import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.ArrayUtil;

import java.util.ArrayList;
import java.util.List;

public class ViewerTreeStructure extends AbstractTreeStructure {

  private boolean myShowWhiteSpaces = true;

  private Project myProject;
  private PsiElement myRootPsiElement = null;
  private Object myRootElement = new Object();

  public ViewerTreeStructure(Project project) {
    myProject = project;
  }

  public void setRootPsiElement(PsiElement rootPsiElement) {
    myRootPsiElement = rootPsiElement;
  }

  public PsiElement getRootPsiElement() {
    return myRootPsiElement;
  }

  public Object getRootElement() {
    return myRootElement;
  }

  public Object[] getChildElements(final Object element) {
    if (myRootElement == element) {
      if (myRootPsiElement == null) {
        return ArrayUtil.EMPTY_OBJECT_ARRAY;
      }
      return myRootPsiElement instanceof PsiFile ? ((PsiFile)myRootPsiElement).getPsiRoots() : new Object[]{myRootPsiElement};
    }
    final Object[][] children = new Object[1][];
    children[0] = ArrayUtil.EMPTY_OBJECT_ARRAY;
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        PsiElement[] elementChildren = ((PsiElement)element).getChildren();
        if (elementChildren.length > 0) {
          List<PsiElement> childrenList = new ArrayList<PsiElement>(elementChildren.length);
          for (int idx = 0; idx < elementChildren.length; idx++) {
            PsiElement psiElement = elementChildren[idx];
            if (!myShowWhiteSpaces && psiElement instanceof PsiWhiteSpace) {
              continue;
            }
            childrenList.add(psiElement);
          }
          elementChildren = childrenList.toArray(new PsiElement[childrenList.size()]);
        }
        children[0] = elementChildren;
      }
    });
    return children[0];
  }

  public Object getParentElement(Object element) {
    return null;
    //if (element == myRootElement) {
    //  return null;
    //}
    //if (element == myRootPsiElement) {
    //  return myRootElement;
    //}
    //PsiElement parent = ((PsiElement)element).getParent();
    //return parent;
  }

  public void commit() {
  }

  public boolean hasSomethingToCommit() {
    return false;
  }

  public NodeDescriptor createDescriptor(Object element, NodeDescriptor parentDescriptor) {
    if (element == myRootElement) {
      return new NodeDescriptor(myProject, null) {
        public boolean update() {
          return false;
        }
        public Object getElement() {
          return myRootElement;
        }
      };
    }
    return new ViewerNodeDescriptor(myProject, (PsiElement)element, parentDescriptor);
  }

  public boolean isShowWhiteSpaces() {
    return myShowWhiteSpaces;
  }

  public void setShowWhiteSpaces(boolean showWhiteSpaces) {
    myShowWhiteSpaces = showWhiteSpaces;
  }
}
