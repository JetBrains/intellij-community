package com.intellij.ide.favoritesTreeView;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.InputValidator;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.LayeredIcon;

/**
 * User: anna
 * Date: Feb 24, 2005
 */
public class AddNewFavoritesListAction extends AnAction{
  public static LayeredIcon ourIcon = new LayeredIcon(2);
  static {
    ourIcon.setIcon(IconLoader.getIcon("/general/favorites.png"), 1);
    ourIcon.setIcon(IconLoader.getIcon("/general/add.png"), 0, 2, 2);
  }
  public AddNewFavoritesListAction() {
    super("Add New Favorites List", "Add New Favorites List", ourIcon);
  }

  public void actionPerformed(AnActionEvent e) {
    final Project project = (Project)e.getDataContext().getData(DataConstants.PROJECT);
    if (project != null){
      final FavoritesTreeViewPanel favoritesTreeViewPanel = doAddNewFavoritesList(project);
      final ToolWindowManager windowManager = ToolWindowManager.getInstance(project);
      windowManager.getToolWindow(ToolWindowId.FAVORITES_VIEW).activate(null);
      final FavoritesViewImpl favoritesView = FavoritesViewImpl.getInstance(project);
      favoritesView.setSelectedContent(favoritesView.getContent(favoritesTreeViewPanel));
    }
  }

  public FavoritesTreeViewPanel doAddNewFavoritesList(final Project project) {
    final String s =
      Messages.showInputDialog(project, "Input new favorites list name", "Add New Favorites List", Messages.getInformationIcon(), "new",
                               new InputValidator() {
                                     public boolean checkInput(String inputString) {
                                       return inputString != null && inputString.trim().length() > 0;
                      }

                                     public boolean canClose(String inputString) {
                                       return inputString != null && inputString.trim().length() > 0;
                      }
                    });
    final FavoritesViewImpl favoritesView = FavoritesViewImpl.getInstance(project);
    return favoritesView.addNewFavoritesList(s);
  }


}
