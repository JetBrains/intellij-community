package com.intellij.ide.favoritesTreeView;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ide.IdeBundle;

/**
 * User: anna
 * Date: Feb 23, 2005
 */
public class DeleteFromFavoritesAction extends AnAction {
  public DeleteFromFavoritesAction() {
    super(IdeBundle.message("action.remove.from.current.favorites"),
          IdeBundle.message("action.remove.selected.favorite"), IconLoader.getIcon("/general/remove.png"));
  }

  public void actionPerformed(AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    Project project = (Project)dataContext.getData(DataConstants.PROJECT);
    if (project == null) {
      return;
    }
    FavoritesManager favoritesManager = FavoritesManager.getInstance(project);
    FavoritesTreeNodeDescriptor[] roots = (FavoritesTreeNodeDescriptor[])dataContext.getData(FavoritesTreeViewPanel.CONTEXT_FAVORITES_ROOTS);
    String listName = (String)dataContext.getData(FavoritesTreeViewPanel.FAVORITES_LIST_NAME);
    for (FavoritesTreeNodeDescriptor root : roots) {
      favoritesManager.removeRoot(listName, root.getElement().getValue());
    }
  }

  public void update(AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    Project project = (Project)dataContext.getData(DataConstants.PROJECT);
    if (project == null){
      e.getPresentation().setEnabled(false);
      return;
    }
    FavoritesTreeNodeDescriptor[] roots = (FavoritesTreeNodeDescriptor[])dataContext.getData(FavoritesTreeViewPanel.CONTEXT_FAVORITES_ROOTS);
    e.getPresentation().setEnabled(roots != null && roots.length != 0);
  }
}
