/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.ide.favoritesTreeView.actions;

import com.intellij.ide.actions.QuickSwitchSchemeAction;
import com.intellij.ide.favoritesTreeView.FavoritesManager;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.Project;

/**
 * User: anna
 * Date: Feb 24, 2005
 */
public class AddToFavoritesPopupAction extends QuickSwitchSchemeAction {
  protected void fillActions(Project project, DefaultActionGroup group) {
    group.removeAll();
    final String[] availableFavoritesLists = FavoritesManager.getInstance(project).getAvailableFavoritesLists();
    for (String favoritesList : availableFavoritesLists) {
      group.add(new AddToFavoritesAction(favoritesList));
    }
    group.add(new AddToNewFavoritesListAction());
  }

  protected boolean isEnabled() {
    return true;
  }
}
