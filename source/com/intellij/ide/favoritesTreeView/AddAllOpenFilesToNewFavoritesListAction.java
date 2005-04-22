package com.intellij.ide.favoritesTreeView;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;

/**
 * User: anna
 * Date: Feb 28, 2005
 */
class AddAllOpenFilesToNewFavoritesListAction extends AnAction {
 public AddAllOpenFilesToNewFavoritesListAction() {
   super("Add All Open Tabs To New Favorites List", "Add To New Favorites List", IconLoader.getIcon("/general/addFavoritesList.png"));
 }

 public void actionPerformed(AnActionEvent e) {
   final DataContext dataContext = e.getDataContext();
   final AddNewFavoritesListAction action = (AddNewFavoritesListAction)ActionManager.getInstance().getAction(IdeActions.ADD_NEW_FAVORITES_LIST);
   final FavoritesTreeViewPanel favoritesTreeViewPanel = action.doAddNewFavoritesList((Project)dataContext.getData(DataConstants.PROJECT));
   if (favoritesTreeViewPanel != null){
     new AddAllOpenFilesToFavorites(favoritesTreeViewPanel.getName()).actionPerformed(e);
   }
 }
}
