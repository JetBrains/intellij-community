package com.intellij.ide.favoritesTreeView;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;

/**
 * User: anna
 * Date: Feb 23, 2005
 */
public class DeleteFromFavoritesAction extends AnAction {
  public DeleteFromFavoritesAction() {
    super("Remove From Current Favorites", "Remove Selected Favorite", IconLoader.getIcon("/general/remove.png"));
  }

  public void actionPerformed(AnActionEvent e) {    
    final DataContext dataContext = e.getDataContext();
    Project project = (Project)dataContext.getData(DataConstants.PROJECT);
    if (project == null) {
      return;
    }
    if (e.getPlace().equals(ActionPlaces.FAVORITES_VIEW_POPUP) || e.getPlace().equals(ActionPlaces.FAVORITES_VIEW_TOOLBAR)) {
      final FavoritesTreeViewPanel currentTreeViewPanel = FavoritesViewImpl.getInstance(project).getCurrentTreeViewPanel();
      FavoritesTreeNodeDescriptor[] selectedNodeDescriptors = currentTreeViewPanel
          .getSelectedNodeDescriptors();
      removeNodes(selectedNodeDescriptors, project, currentTreeViewPanel.getName());
    }
  }

  public static void removeNodes(final FavoritesTreeNodeDescriptor[] selectedNodeDescriptors, final Project project, String favoritesViewPane) {
    final FavoritesTreeViewPanel favoritesTreeViewPanel = FavoritesViewImpl.getInstance(project).getFavoritesTreeViewPanel(favoritesViewPane);
    for (int i = 0; selectedNodeDescriptors != null && i < selectedNodeDescriptors.length; i++) {
      FavoritesTreeNodeDescriptor selectedNodeDescriptor = selectedNodeDescriptors[i];
      selectedNodeDescriptor = FavoritesTreeNodeDescriptor.getFavoritesRoot(selectedNodeDescriptor, project, favoritesViewPane);
      if (selectedNodeDescriptor != null) {
        favoritesTreeViewPanel.removeFromFavorites(selectedNodeDescriptor.getElement());
      }
    }
  }



  public void update(AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    Project project = (Project)dataContext.getData(DataConstants.PROJECT);
    if (project == null){
      e.getPresentation().setEnabled(false);
      return;
    }
    final FavoritesTreeNodeDescriptor[] selectedNodeDescriptors = FavoritesViewImpl.getInstance(project).getCurrentTreeViewPanel()
        .getSelectedNodeDescriptors();
    e.getPresentation()
      .setEnabled(selectedNodeDescriptors != null && selectedNodeDescriptors.length > 0 && !(selectedNodeDescriptors[0].getElement().getValue() instanceof String));
  }


}
