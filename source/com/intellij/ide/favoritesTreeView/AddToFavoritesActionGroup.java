package com.intellij.ide.favoritesTreeView;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.Project;

/**
 * User: anna
 * Date: Mar 3, 2005
 */
public class AddToFavoritesActionGroup extends DefaultActionGroup{
  public void update(AnActionEvent e) {
    removeAll();
    final Project project = (Project)e.getDataContext().getData(DataConstants.PROJECT);
    if (project == null){
      return;
    }
    final String[] availableFavoritesLists = FavoritesViewImpl.getInstance(project).getAvailableFavoritesLists();
    for (int i = 0; i < availableFavoritesLists.length; i++) {
      String favoritesList = availableFavoritesLists[i];
      add(new AddToFavoritesAction(favoritesList));
    }
    addSeparator();
    add(new AddToNewFavoritesListAction());
  }
}
