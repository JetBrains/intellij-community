package com.intellij.ide.favoritesTreeView;

import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.project.Project;

/**
 * User: anna
 * Date: Feb 15, 2005
 */
public class FavoritesTreeNodeDescriptor extends NodeDescriptor<AbstractTreeNode> {
  private AbstractTreeNode myElement;

  public FavoritesTreeNodeDescriptor(final Project project, final NodeDescriptor parentDescriptor, final AbstractTreeNode element) {
    super(project, parentDescriptor);
    myElement = element;
    myOpenIcon = myElement.getPresentation().getIcon(true);
    myClosedIcon = myElement.getPresentation().getIcon(false);
    myName = myElement.getPresentation().getPresentableText();
  }

  public boolean update() {
    myElement.update();
    myName = myElement.getPresentation().getPresentableText();
    return true;
  }

  public AbstractTreeNode getElement() {
    return myElement;
  }

  public boolean equals(Object object) {
    if (!(object instanceof FavoritesTreeNodeDescriptor)) return false;
    return ((FavoritesTreeNodeDescriptor)object).getElement().equals(myElement);
  }

  public int hashCode() {
    return myElement.hashCode();
  }
}
