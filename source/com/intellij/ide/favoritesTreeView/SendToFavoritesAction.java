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
    Project project = (Project)e.getDataContext().getData(DataConstants.PROJECT);
    final FavoritesViewImpl favoritesView = FavoritesViewImpl.getInstance(project);
    favoritesView.getAddToFavoritesAction(myName).actionPerformed(e);
    ((DeleteFromFavoritesAction)ActionManager.getInstance().getAction(IdeActions.REMOVE_FROM_FAVORITES)).actionPerformed(e);
  }

  public void update(AnActionEvent e) {

  }
}
