package com.intellij.ide.favoritesTreeView;

import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;

/**
 * User: anna
 * Date: Feb 23, 2005
 */
public class DeleteFromFavoritesAction extends AnAction {
  public DeleteFromFavoritesAction() {
    super("Remove From Favorites", "Remove Selected Favorite", IconLoader.getIcon("/actions/cancel.png"));
  }

  public void actionPerformed(AnActionEvent e) {    
    final DataContext dataContext = e.getDataContext();
    Project project = (Project)dataContext.getData(DataConstants.PROJECT);
    if (project == null) {
      return;
    }
    if (e.getPlace().equals(ActionPlaces.FAVORITES_VIEW_POPUP) || e.getPlace().equals(ActionPlaces.FAVORITES_VIEW_TOOLBAR)) {
      FavoritesTreeNodeDescriptor[] selectedNodeDescriptors = FavoritesViewImpl.getInstance(project).getCurrentTreeViewPanel()
          .getSelectedNodeDescriptors();
      for (int i = 0; i < selectedNodeDescriptors.length; i++) {
        FavoritesTreeNodeDescriptor selectedNodeDescriptor = selectedNodeDescriptors[i];
        selectedNodeDescriptor = getFavoritesRoot(selectedNodeDescriptor, project);
        if (selectedNodeDescriptor != null) {
          FavoritesViewImpl.getInstance(project).getCurrentTreeViewPanel().removeFromFavorites((AbstractTreeNode)selectedNodeDescriptor.getElement());
        }
      }
    }
  }

  private FavoritesTreeNodeDescriptor getFavoritesRoot(FavoritesTreeNodeDescriptor node, Project project) {
    while (node.getParentDescriptor() != null && node.getParentDescriptor() instanceof FavoritesTreeNodeDescriptor) {
      FavoritesTreeNodeDescriptor favoritesDescriptor = (FavoritesTreeNodeDescriptor)node.getParentDescriptor();
      if (favoritesDescriptor.getElement() == FavoritesViewImpl.getInstance(project).getCurrentTreeViewPanel().getFavoritesTreeStructure().getRootElement()) {
        return node;
      }
      node = favoritesDescriptor;
    }
    return node;
  }

  public void update(AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    Project project = (Project)dataContext.getData(DataConstants.PROJECT);
    e.getPresentation()
      .setEnabled(project != null &&
                                  FavoritesViewImpl.getInstance(project).getCurrentTreeViewPanel()
                                    .getSelectedNodeDescriptors() != null);
  }


}
