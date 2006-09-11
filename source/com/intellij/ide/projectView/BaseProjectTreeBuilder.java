package com.intellij.ide.projectView;

import com.intellij.ide.favoritesTreeView.FavoritesTreeNodeDescriptor;
import com.intellij.ide.projectView.impl.ProjectAbstractTreeStructureBase;
import com.intellij.ide.util.treeView.AbstractTreeBuilder;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.util.StatusBarProgress;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeNode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public abstract class BaseProjectTreeBuilder extends AbstractTreeBuilder {
  protected final Project myProject;

  public BaseProjectTreeBuilder(Project project, JTree tree, DefaultTreeModel treeModel, ProjectAbstractTreeStructureBase treeStructure, Comparator<NodeDescriptor> comparator) {
    super(tree, treeModel, treeStructure, comparator);
    myProject = project;
  }

  protected boolean isAlwaysShowPlus(NodeDescriptor nodeDescriptor) {
    return ((AbstractTreeNode)nodeDescriptor).isAlwaysShowPlus();
  }

  protected boolean isAutoExpandNode(NodeDescriptor nodeDescriptor) {
    return nodeDescriptor.getParentDescriptor() == null;
  }

  protected final void expandNodeChildren(final DefaultMutableTreeNode node) {
    Object element = ((NodeDescriptor)node.getUserObject()).getElement();
    VirtualFile[] virtualFiles = getFilesToRefresh(element);
    super.expandNodeChildren(node);
    for (VirtualFile virtualFile : virtualFiles) {
      virtualFile.refresh(true, false);
    }
  }

  protected static VirtualFile[] getFilesToRefresh(Object element) {
    final VirtualFile virtualFile;
    if (element instanceof PsiDirectory){
      virtualFile = ((PsiDirectory)element).getVirtualFile();
    }
    else if (element instanceof PsiFile){
      virtualFile = ((PsiFile)element).getVirtualFile();
    }
    else{
      virtualFile = null;
    }
    return virtualFile != null ? new VirtualFile[]{virtualFile} : VirtualFile.EMPTY_ARRAY;
  }

  private List<AbstractTreeNode> getOrBuildChildren(AbstractTreeNode parent) {
    buildNodeForElement(parent);

    DefaultMutableTreeNode node = getNodeForElement(parent);

    if (node == null) {
      return new ArrayList<AbstractTreeNode>();
    }

    myTree.expandPath(new TreePath(node.getPath()));

    int childCount = node.getChildCount();
    List<AbstractTreeNode> result = new ArrayList<AbstractTreeNode>(childCount);
    for (int i = 0; i < childCount; i++) {
      TreeNode childAt = node.getChildAt(i);
      DefaultMutableTreeNode defaultMutableTreeNode = (DefaultMutableTreeNode)childAt;
      if (defaultMutableTreeNode.getUserObject() instanceof AbstractTreeNode) {
        ProjectViewNode treeNode = (ProjectViewNode)defaultMutableTreeNode.getUserObject();
        result.add(treeNode);
      }
      else if (defaultMutableTreeNode.getUserObject() instanceof FavoritesTreeNodeDescriptor) {
        AbstractTreeNode treeNode = ((FavoritesTreeNodeDescriptor)defaultMutableTreeNode.getUserObject()).getElement();
        result.add(treeNode);
      }
    }
    return result;
  }

  public void hideChildrenFor(DefaultMutableTreeNode node) {
    if (node != null){
      final JTree tree = getTree();
      final TreePath path = new TreePath(node.getPath());
      if (tree.isExpanded(path)) {
        tree.collapsePath(path);
      }
    }
  }

  public void select(Object element, VirtualFile file, boolean requestFocus) {
    DefaultMutableTreeNode selected = alreadySelectedNode(element);
    if (selected == null) {
      AbstractTreeNode node = select((AbstractTreeNode)getTreeStructure().getRootElement(), file, element, new Condition<AbstractTreeNode>() {
        public boolean value(final AbstractTreeNode object) {
          return true;
        }
      });
      selected = getNodeForElement(node);
    }
    TreeUtil.selectInTree(selected, requestFocus, getTree());
  }

  public void selectInWidth(final Object element, final boolean requestFocus, final Condition<AbstractTreeNode> nonStopCondition) {
    DefaultMutableTreeNode selected = alreadySelectedNode(element);
    if (selected == null) {
      AbstractTreeNode node = select((AbstractTreeNode)getTreeStructure().getRootElement(), null, element, nonStopCondition);
      selected = getNodeForElement(node);
    }
    TreeUtil.selectInTree(selected, requestFocus, getTree());
  }

  // returns selected node for element or null if element node is not selected
  private DefaultMutableTreeNode alreadySelectedNode(final Object element) {
    final TreePath[] selectionPaths = myTree.getSelectionPaths();
    if (selectionPaths == null || selectionPaths.length == 0) {
      return null;
    }
    for (TreePath selectionPath : selectionPaths) {
      Object selected = selectionPath.getLastPathComponent();
      if (elementIsEqualTo(selected, element)){
        return (DefaultMutableTreeNode)selected;
      }
    }
    return null;
  }

  private static boolean elementIsEqualTo(final Object node, final Object element) {
    if (node instanceof DefaultMutableTreeNode) {
      final Object userObject = ((DefaultMutableTreeNode)node).getUserObject();
      if (userObject instanceof ProjectViewNode) {
        final ProjectViewNode projectViewNode = (ProjectViewNode)userObject;
        return projectViewNode.canRepresent(element);
      }
    }
    return false;
  }

  private AbstractTreeNode select(AbstractTreeNode current, VirtualFile file, Object element, Condition<AbstractTreeNode> nonStopCondition) {
    if (current.canRepresent(element)) return current;
    if (current instanceof ProjectViewNode && file != null && !((ProjectViewNode)current).contains(file)) return null;

    DefaultMutableTreeNode currentNode = getNodeForElement(current);
    boolean expanded = currentNode != null && getTree().isExpanded(new TreePath(currentNode.getPath()));

    List<AbstractTreeNode> kids = getOrBuildChildren(current);
    for (AbstractTreeNode node : kids) {
      if (nonStopCondition.value(node)) {
        AbstractTreeNode result = select(node, file, element, nonStopCondition);
        if (result != null) {
          currentNode = getNodeForElement(current);
          if (currentNode != null) {
            final TreePath path = new TreePath(currentNode.getPath());
            if (!getTree().isExpanded(path)) {
              getTree().expandPath(path);
            }
          }
          return result;
        }
        else {
          if (!expanded) {
            hideChildrenFor(currentNode);
          }
        }
      }
    }

    return null;
  }

  @NotNull
  protected ProgressIndicator createProgressIndicator() {
    return new StatusBarProgress();
  }
}
