package com.intellij.ide.favoritesTreeView;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.ide.IdeBundle;
import org.jetbrains.annotations.Nullable;

/**
 * User: anna
 * Date: Feb 24, 2005
 */
public class SendToFavoritesGroup extends ActionGroup{

  public AnAction[] getChildren(@Nullable AnActionEvent e) {
    if (e == null) return AnAction.EMPTY_ARRAY;
    final Project project = (Project)e.getDataContext().getData(DataConstants.PROJECT);
    if (project == null){
      return AnAction.EMPTY_ARRAY;
    }
    final FavoritesViewImpl favoritesView = FavoritesViewImpl.getInstance(project);
    final FavoritesTreeNodeDescriptor[] selectedNodeDescriptors = favoritesView.getCurrentTreeViewPanel().getSelectedNodeDescriptors();
    final String[] allAddActionNamesButThis = favoritesView.getAllAddActionNamesButThis();
    if (selectedNodeDescriptors == null ||
        allAddActionNamesButThis == null){
      return AnAction.EMPTY_ARRAY;
    }
    int idx = 0;
    AnAction[] actions = new AnAction[allAddActionNamesButThis.length + 2];
    for (String addAction : allAddActionNamesButThis) {
      actions[idx++] = new SendToFavoritesAction(addAction);
    }
    actions[idx++] = Separator.getInstance();
    actions[idx] = new SendToNewFavoritesListAction();
    return actions;
  }

  private static class SendToNewFavoritesListAction extends AnAction {
    public SendToNewFavoritesListAction() {
      super(IdeBundle.message("action.send.to.new.favorites.list"));
    }

    public void actionPerformed(AnActionEvent e) {
      final DataContext dataContext = e.getDataContext();
      final FavoritesTreeViewPanel favoritesTreeViewPanel = AddNewFavoritesListAction.doAddNewFavoritesList((Project)dataContext.getData(DataConstants.PROJECT));
      if (favoritesTreeViewPanel != null) {
        new SendToFavoritesAction(favoritesTreeViewPanel.getName()).actionPerformed(e);
      }
    }
  }
}
