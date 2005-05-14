package com.intellij.ide.favoritesTreeView;

import com.intellij.ide.projectView.ViewSettings;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;

/**
 * User: anna
 * Date: Feb 28, 2005
 */
class AddToNewFavoritesListAction extends AnAction {
 public AddToNewFavoritesListAction() {
   super("Add To New Favorites List", "Add To New Favorites List", IconLoader.getIcon("/general/addFavoritesList.png"));
 }

 public void actionPerformed(AnActionEvent e) {
   final DataContext dataContext = e.getDataContext();
   final FavoritesTreeViewPanel favoritesTreeViewPanel = AddNewFavoritesListAction.doAddNewFavoritesList((Project)dataContext.getData(DataConstants.PROJECT));
   if (favoritesTreeViewPanel != null) {
     new AddToFavoritesAction(favoritesTreeViewPanel.getName()).actionPerformed(e);
   }
 }

  public void update(AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    Project project = (Project)dataContext.getData(DataConstants.PROJECT);
    if (project == null) {
      e.getPresentation().setEnabled(false);
    }
    else {
      e.getPresentation().setEnabled(AddToFavoritesAction.createNodes(dataContext, e.getPlace().equals(ActionPlaces.J2EE_VIEW_POPUP) ||
                                                                                   e.getPlace().equals(ActionPlaces.STRUCTURE_VIEW_POPUP) ||
                                                                                   e.getPlace().equals(ActionPlaces.PROJECT_VIEW_POPUP), ViewSettings.DEFAULT) != null);
    }

  }
}
