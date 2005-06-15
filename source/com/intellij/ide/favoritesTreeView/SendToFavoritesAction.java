package com.intellij.ide.favoritesTreeView;

import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;

import java.util.ArrayList;

/**
 * User: anna
 * Date: Feb 24, 2005
 */
public class SendToFavoritesAction extends AnAction{
  private String myName;
  public SendToFavoritesAction(String name) {
    super(name);
    myName = name;
  }

  public void actionPerformed(AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    Project project = (Project)dataContext.getData(DataConstants.PROJECT);
    final FavoritesViewImpl favoritesView = FavoritesViewImpl.getInstance(project);
    final FavoritesTreeViewPanel currentTreeViewPanel = favoritesView.getCurrentTreeViewPanel();
    final FavoritesTreeNodeDescriptor[] selectedNodeDescriptors = currentTreeViewPanel.getSelectedNodeDescriptors();
    ArrayList<AbstractTreeNode> nodesToAdd = new ArrayList<AbstractTreeNode>();
    for (int i = 0; i < selectedNodeDescriptors.length; i++) {
      final FavoritesTreeNodeDescriptor selectedNodeDescriptor = FavoritesTreeNodeDescriptor.getFavoritesRoot(selectedNodeDescriptors[i], project, currentTreeViewPanel.getName());
      nodesToAdd.add(selectedNodeDescriptor.getElement());
    }
    favoritesView.getAddToFavoritesAction(myName).addNodes(project, nodesToAdd.toArray(new AbstractTreeNode[nodesToAdd.size()]));
    DeleteFromFavoritesAction.removeNodes(selectedNodeDescriptors, project, currentTreeViewPanel.getName());
  }

  public void update(AnActionEvent e) {

  }
}
