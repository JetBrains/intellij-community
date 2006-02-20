package com.intellij.ide.favoritesTreeView;

import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;

/**
 * User: anna
 * Date: Feb 28, 2005
 */
class AddToNewFavoritesListAction extends AnAction {
 public AddToNewFavoritesListAction() {
   super(IdeBundle.message("action.add.to.new.favorites.list"),
         IdeBundle.message("action.add.to.new.favorites.list"), IconLoader.getIcon("/general/addFavoritesList.png"));
 }

 public void actionPerformed(AnActionEvent e) {
   final DataContext dataContext = e.getDataContext();
   final String newName = AddNewFavoritesListAction.doAddNewFavoritesList((Project)dataContext.getData(DataConstants.PROJECT));
   if (newName != null) {
     new AddToFavoritesAction(newName).actionPerformed(e);
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
