package com.intellij.ide.projectView.impl.nodes;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.Collection;
import java.util.Iterator;

public class FormNode extends ProjectViewNode<Form>{
  private final Collection<AbstractTreeNode> myChildren;
  public FormNode(Project project, Form value, ViewSettings viewSettings,
                  Collection<AbstractTreeNode> children) {
    super(project, value, viewSettings);
    myChildren = children;    
  }

  public Collection<AbstractTreeNode> getChildren() {
    return myChildren;
  }

  public String getTestPresentation() {
    return "Form:" + getValue().getName();
  }

  public boolean contains(VirtualFile file) {
    for (Iterator<AbstractTreeNode> iterator = myChildren.iterator(); iterator.hasNext();) {
      ProjectViewNode treeNode = (ProjectViewNode)iterator.next();
      if (treeNode.contains(file)) return true;
    }
    return false;
  }

  public void update(PresentationData presentation) {
    presentation.setPresentableText(getValue().getName());
    presentation.setIcons(StdFileTypes.GUI_DESIGNER_FORM.getIcon());
  }

  public void navigate(final boolean requestFocus) {
    getValue().navigate(requestFocus);
  }

  public boolean canNavigate() {
    return getValue().canNavigate();
  }
}
