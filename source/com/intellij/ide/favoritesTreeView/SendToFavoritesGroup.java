package com.intellij.ide.favoritesTreeView;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;

/**
 * User: anna
 * Date: Feb 24, 2005
 */
public class SendToFavoritesGroup extends DefaultActionGroup{
  
  public void update(AnActionEvent e) {
    Project project = (Project)e.getDataContext().getData(DataConstants.PROJECT);
    final FavoritesViewImpl favoritesView = FavoritesViewImpl.getInstance(project);
    final FavoritesTreeNodeDescriptor[] selectedNodeDescriptors = favoritesView.getCurrentTreeViewPanel().getSelectedNodeDescriptors();
    final String[] allAddActionNamesButThis = favoritesView.getAllAddActionNamesButThis();
    e.getPresentation().setEnabled(e.getPlace().equals(ActionPlaces.FAVORITES_VIEW_POPUP) &&
                                   selectedNodeDescriptors != null &&
                                   allAddActionNamesButThis != null);
    removeAll();
    for (int i = 0; allAddActionNamesButThis != null && i < allAddActionNamesButThis.length; i++) {
      String addAction = allAddActionNamesButThis[i];
      add(new SendToFavoritesAction(addAction));
    }
    addSeparator();
    add(new SendToNewFavoritesListAction());
  }

  private static class SendToNewFavoritesListAction extends AnAction {
    public SendToNewFavoritesListAction() {
      super("Send To New Favorites List");
    }

    public void actionPerformed(AnActionEvent e) {
      final DataContext dataContext = e.getDataContext();
      final AddNewFavoritesListAction action = (AddNewFavoritesListAction)ActionManager.getInstance().getAction(IdeActions.ADD_NEW_FAVORITES_LIST);
      final FavoritesTreeViewPanel favoritesTreeViewPanel = action.doAddNewFavoritesList((Project)dataContext.getData(DataConstants.PROJECT));
      new SendToFavoritesAction(favoritesTreeViewPanel.getName()).actionPerformed(e);
    }
  }
}
