/**
 * class ViewerNodeDescriptor
 * created Aug 25, 2001
 * @author Jeka
 */
package com.intellij.internal.psiView;

import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;

public class ViewerNodeDescriptor extends NodeDescriptor {
  private PsiElement myElement;

  public ViewerNodeDescriptor(Project project, PsiElement element, NodeDescriptor parentDescriptor) {
    super(project, parentDescriptor);
    myElement = element;
    myName = myElement.toString();
  }

  public boolean update() {
    return false;
  }

  public Object getElement() {
    return myElement;
  }
}
