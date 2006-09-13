package com.intellij.ide.util.treeView;

import com.intellij.ide.projectView.TreeStructureProvider;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public abstract class AbstractTreeStructureBase extends AbstractTreeStructure {
  protected final Project myProject;

  static private final Logger LOG = Logger.getInstance("#com.intellij.ide.util.treeView.AbstractTreeStructureBase");


  protected AbstractTreeStructureBase(Project project) {
    myProject = project;
  }

  public Object[] getChildElements(Object element) {
    LOG.assertTrue(element instanceof AbstractTreeNode, element.getClass().getName());
    AbstractTreeNode<?> treeNode = (AbstractTreeNode)element;
    Collection<? extends AbstractTreeNode> elements = treeNode.getChildren();
    List<TreeStructureProvider> providers = getProviders();
    ArrayList<AbstractTreeNode> modified = new ArrayList<AbstractTreeNode>(elements);
    if (providers != null) {
      for (TreeStructureProvider provider : providers) {
        modified = new ArrayList<AbstractTreeNode>(provider.modify(treeNode, modified, ViewSettings.DEFAULT));
      }
    }
    elements = modified;
    for (AbstractTreeNode node : elements) {
      node.setParent(treeNode);
    }
    return modified.toArray(new Object[modified.size()]);
  }

  public Object getParentElement(Object element) {
    if (element instanceof AbstractTreeNode){
      return ((AbstractTreeNode)element).getParent();
    } else {
      return null;
    }
  }

  @NotNull
  public NodeDescriptor createDescriptor(final Object element, final NodeDescriptor parentDescriptor) {
    return (AbstractTreeNode)element;
  }

  @Nullable
  public abstract List<TreeStructureProvider> getProviders();

  public Object getDataFromProviders(final List<AbstractTreeNode> selectedNodes, final String dataId) {
    final List<TreeStructureProvider> providers = getProviders();
    if (providers != null) {
      for (TreeStructureProvider treeStructureProvider : providers) {
        final Object fromProvider = treeStructureProvider.getData(selectedNodes, dataId);
        if (fromProvider != null) {
          return fromProvider;
        }
      }
    }
    return null;
  }
}
