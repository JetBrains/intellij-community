/**
 * class ViewerNodeDescriptor
 * created Aug 25, 2001
 * @author Jeka
 */
package com.intellij.internal.psiView;

import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.project.Project;

public class ViewerNodeDescriptor extends NodeDescriptor {
  private Object myElement;

  public ViewerNodeDescriptor(Project project, Object element, NodeDescriptor parentDescriptor) {
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
