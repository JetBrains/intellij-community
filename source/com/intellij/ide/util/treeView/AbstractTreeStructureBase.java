package com.intellij.ide.util.treeView;

import com.intellij.ide.projectView.TreeStructureProvider;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

public abstract class AbstractTreeStructureBase extends AbstractTreeStructure {
  protected final Project myProject;

  static private final Logger LOG = Logger.getInstance("#com.intellij.ide.util.treeView.AbstractTreeStructureBase");


  protected AbstractTreeStructureBase(Project project) {
    myProject = project;
  }

  public Object[] getChildElements(Object element) {
    LOG.assertTrue(element instanceof AbstractTreeNode, element.getClass().getName());
    AbstractTreeNode treeNode = ((AbstractTreeNode)element);
    Collection<AbstractTreeNode> elements = treeNode.getChildren();
    List providers = getProviders();
    ArrayList<AbstractTreeNode> modified = elements != null ? new ArrayList<AbstractTreeNode>(elements) : new ArrayList<AbstractTreeNode>();
    for (Iterator iterator = providers.iterator(); iterator.hasNext();) {
      TreeStructureProvider provider = (TreeStructureProvider)iterator.next();
      modified = new ArrayList<AbstractTreeNode>(provider.modify(treeNode, modified, ViewSettings.DEFAULT));
    }
    elements = modified;
    for (Iterator<AbstractTreeNode> iterator = elements.iterator(); iterator.hasNext();) {
      AbstractTreeNode node = iterator.next();
      node.setParent(treeNode);
    }
    return (Object[])modified.toArray(new Object[modified.size()]);
  }

  public Object getParentElement(Object element) {
    if (element instanceof AbstractTreeNode){
      return ((AbstractTreeNode)element).getParent();
    } else {
      return null;
    }
  }

  public NodeDescriptor createDescriptor(final Object element, final NodeDescriptor parentDescriptor) {
    return ((AbstractTreeNode)element);
  }

  public abstract List<TreeStructureProvider> getProviders();
}
