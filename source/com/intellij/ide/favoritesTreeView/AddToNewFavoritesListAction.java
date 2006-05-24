package com.intellij.ide.favoritesTreeView;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;

import java.util.Collection;

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
   Project project = (Project)dataContext.getData(DataConstants.PROJECT);
   Collection<AbstractTreeNode> nodesToAdd = AddToFavoritesAction.getNodesToAdd(dataContext, true);
   if (nodesToAdd != null) {
     final String newName = AddNewFavoritesListAction.doAddNewFavoritesList((Project)dataContext.getData(DataConstants.PROJECT));
     if (newName != null) {
       FavoritesManager.getInstance(project).addRoots(newName, nodesToAdd);
     }
   }
 }

  public void update(AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    Project project = (Project)dataContext.getData(DataConstants.PROJECT);
    if (project == null) {
      e.getPresentation().setEnabled(false);
    }
    else {
      e.getPresentation().setEnabled(AddToFavoritesAction.canCreateNodes(dataContext, e));
    }
  }
}
