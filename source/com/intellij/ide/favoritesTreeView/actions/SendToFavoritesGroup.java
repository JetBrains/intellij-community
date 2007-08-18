package com.intellij.ide.favoritesTreeView.actions;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.favoritesTreeView.FavoritesManager;
import com.intellij.ide.favoritesTreeView.FavoritesTreeNodeDescriptor;
import com.intellij.ide.favoritesTreeView.FavoritesTreeViewPanel;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * User: anna
 * Date: Feb 24, 2005
 */
public class SendToFavoritesGroup extends ActionGroup{

  public AnAction[] getChildren(@Nullable AnActionEvent e) {
    if (e == null) return AnAction.EMPTY_ARRAY;
    final Project project = DataKeys.PROJECT.getData(e.getDataContext());
    if (project == null){
      return AnAction.EMPTY_ARRAY;
    }
    final FavoritesManager favoritesManager = FavoritesManager.getInstance(project);
    final DataContext dataContext = e.getDataContext();
    FavoritesTreeNodeDescriptor[] roots = (FavoritesTreeNodeDescriptor[])dataContext.getData(FavoritesTreeViewPanel.CONTEXT_FAVORITES_ROOTS);
    String listName = (String)dataContext.getData(FavoritesTreeViewPanel.FAVORITES_LIST_NAME);
    if (roots == null || roots.length == 0 || listName == null) {
      return AnAction.EMPTY_ARRAY;
    }

    final String[] allLists = favoritesManager.getAvailableFavoritesLists();
    List<AnAction> actions = new ArrayList<AnAction>();

    for (String list : allLists) {
      if (!list.equals(listName)) {
        actions.add(new SendToFavoritesAction(list));
      }
    }
    if (actions.size() != 0) {
      actions.add(Separator.getInstance());
    }
    actions.add(new SendToNewFavoritesListAction());
    return actions.toArray(new AnAction[actions.size()]);
  }

  private static class SendToNewFavoritesListAction extends AnAction {
    public SendToNewFavoritesListAction() {
      super(IdeBundle.message("action.send.to.new.favorites.list"));
    }

    public void actionPerformed(AnActionEvent e) {
      final DataContext dataContext = e.getDataContext();
      Project project = DataKeys.PROJECT.getData(dataContext);
      FavoritesTreeNodeDescriptor[] roots = (FavoritesTreeNodeDescriptor[])dataContext.getData(FavoritesTreeViewPanel.CONTEXT_FAVORITES_ROOTS);
      String listName = (String)dataContext.getData(FavoritesTreeViewPanel.FAVORITES_LIST_NAME);
      
      String newName = AddNewFavoritesListAction.doAddNewFavoritesList(project);
      if (newName != null) {
        new SendToFavoritesAction(newName).doSend(FavoritesManager.getInstance(project), roots, listName);
      }
    }
  }
}
