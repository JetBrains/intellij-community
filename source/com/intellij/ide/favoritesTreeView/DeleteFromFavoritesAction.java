package com.intellij.ide.favoritesTreeView;

import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;

/**
 * User: anna
 * Date: Feb 24, 2005
 */
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
    if (e.getPlace() == ActionPlaces.FAVORITES_VIEW_POPUP) {
      FavoritesTreeNodeDescriptor[] selectedNodeDescriptors = FavoritesViewImpl.getInstance(project).getFavoritesTreeViewPanel()
          .getSelectedNodeDescriptors();
      for (int i = 0; i < selectedNodeDescriptors.length; i++) {
        FavoritesTreeNodeDescriptor selectedNodeDescriptor = selectedNodeDescriptors[i];
        selectedNodeDescriptor = getFavoritesRoot(selectedNodeDescriptor, project);
        if (selectedNodeDescriptor != null) {
          FavoritesViewImpl.getInstance(project).getFavoritesTreeViewPanel().removeFromFavorites((AbstractTreeNode)selectedNodeDescriptor.getElement());
        }
      }
    }
  }

  private FavoritesTreeNodeDescriptor getFavoritesRoot(FavoritesTreeNodeDescriptor node, Project project) {
    while (node.getParentDescriptor() != null && node.getParentDescriptor() instanceof FavoritesTreeNodeDescriptor) {
      FavoritesTreeNodeDescriptor favoritesDescriptor = (FavoritesTreeNodeDescriptor)node.getParentDescriptor();
      if (favoritesDescriptor.getElement() == FavoritesTreeStructure.getInstance(project).getRootElement()) {
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
                                  FavoritesViewImpl.getInstance(project).getFavoritesTreeViewPanel()
                                    .getSelectedNodeDescriptors() != null);
  }


}
