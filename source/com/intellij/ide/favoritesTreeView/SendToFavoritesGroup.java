package com.intellij.ide.favoritesTreeView;

import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
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
    for (int i = 0; i < allAddActionNamesButThis.length; i++) {
      String addAction = allAddActionNamesButThis[i];
      add(new SendToFavoritesAction(addAction));
    }
  }
}
