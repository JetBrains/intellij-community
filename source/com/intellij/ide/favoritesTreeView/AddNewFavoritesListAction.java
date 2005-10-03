package com.intellij.ide.favoritesTreeView;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.InputValidator;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.util.ArrayUtil;
import com.intellij.ide.IdeBundle;

import java.util.Set;
import java.util.Arrays;

/**
 * User: anna
 * Date: Feb 24, 2005
 */
public class AddNewFavoritesListAction extends AnAction {
  public void actionPerformed(AnActionEvent e) {
    final Project project = (Project)e.getDataContext().getData(DataConstants.PROJECT);
    if (project != null) {
      final FavoritesTreeViewPanel favoritesTreeViewPanel = doAddNewFavoritesList(project);
      if (favoritesTreeViewPanel != null) {
        final ToolWindowManager windowManager = ToolWindowManager.getInstance(project);
        windowManager.getToolWindow(ToolWindowId.FAVORITES_VIEW).activate(null);
        final FavoritesViewImpl favoritesView = FavoritesViewImpl.getInstance(project);
        favoritesView.setSelectedContent(favoritesView.getContent(favoritesTreeViewPanel));
      }
    }
  }

  public static FavoritesTreeViewPanel doAddNewFavoritesList(final Project project) {
    final String s =
      Messages.showInputDialog(project, IdeBundle.message("prompt.input.new.favorites.list.name"), IdeBundle.message("title.add.new.favorites.list"), Messages.getInformationIcon(), getUniqueName(project),
                               new InputValidator() {
                                 public boolean checkInput(String inputString) {
                                   return inputString != null && inputString.trim().length() > 0;
                                 }

                                 public boolean canClose(String inputString) {
                                   final boolean isNew = ArrayUtil.find(FavoritesViewImpl.getInstance(project).getAvailableFavoritesLists(), inputString.trim()) == -1;
                                   if (!isNew) {
                                     Messages.showErrorDialog(project,
                                                              IdeBundle.message("error.favorites.list.already.exists", inputString.trim()), IdeBundle.message("title.unable.to.add.favorites.list"));
                                     return false;
                                   }
                                   return inputString.trim().length() > 0;
                                 }
                               });
    if (s == null || s.length() == 0) return null;
    final FavoritesViewImpl favoritesView = FavoritesViewImpl.getInstance(project);
    return favoritesView.addNewFavoritesList(s);
  }

  private static String getUniqueName(Project project) {
      String[] names = FavoritesViewImpl.getInstance(project).getAvailableFavoritesLists();
      for (int i = 0; ; i++) {
        String newName = IdeBundle.message("favorites.list.unnamed", (i > 0 ? i : ""));
        if (ArrayUtil.find(names, newName) > -1) continue;
        return newName;
      }
    }
}
