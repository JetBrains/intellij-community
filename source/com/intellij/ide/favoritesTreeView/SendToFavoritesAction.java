package com.intellij.ide.favoritesTreeView;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;

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
    favoritesView.getAddToFavoritesAction(myName).actionPerformed(e);
    ((DeleteFromFavoritesAction)ActionManager.getInstance().getAction(IdeActions.REMOVE_FROM_FAVORITES)).removeNodes(selectedNodeDescriptors, project, currentTreeViewPanel.getName());
  }

  public void update(AnActionEvent e) {

  }
}
