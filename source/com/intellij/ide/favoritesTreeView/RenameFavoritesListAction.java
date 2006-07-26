package com.intellij.ide.favoritesTreeView;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.InputValidator;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.IconLoader;
import com.intellij.util.ArrayUtil;

/**
 * User: anna
 * Date: Feb 23, 2005
 */
public class RenameFavoritesListAction extends AnAction {
  public RenameFavoritesListAction() {
    super(IdeBundle.message("action.rename.favorites.list"),
          IdeBundle.message("action.rename.favorites.list"), IconLoader.getIcon("/actions/menu-replace.png"));
  }

  public void actionPerformed(AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    final Project project = (Project)dataContext.getData(DataConstants.PROJECT);
    if (project == null) {
      return;
    }
    final FavoritesManager favoritesManager = FavoritesManager.getInstance(project);
    String listName = (String)dataContext.getData(FavoritesTreeViewPanel.FAVORITES_LIST_NAME);
    final String newName = Messages.showInputDialog(project, IdeBundle.message("prompt.input.favorites.list.new.name"),
                                                        IdeBundle.message("title.rename.favorites.list"), Messages.getInformationIcon(), listName,
                                                        new InputValidator() {
                                                          public boolean checkInput(String inputString) {
                                                            return inputString != null && inputString.trim().length() > 0;
                                                          }

                                                          public boolean canClose(String inputString) {
                                                            String[] lists = favoritesManager.getAvailableFavoritesLists();
                                                            final boolean isNew = ArrayUtil.find(lists, inputString.trim()) == -1;
                                                            if (!isNew) {
                                                              Messages.showErrorDialog(project, IdeBundle.message(
                                                                "error.favorites.list.already.exists", inputString.trim()), IdeBundle.message(
                                                                "title.unable.to.add.favorites.list"));
                                                              return false;
                                                            }
                                                            return inputString.trim().length() > 0;
                                                          }
                                                        });

    if (listName != null) {
      favoritesManager.renameFavoritesList(listName, newName);
    }
  }

  public void update(AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    Project project = (Project)dataContext.getData(DataConstants.PROJECT);
    if (project == null){
      e.getPresentation().setEnabled(false);
      return;
    }
    String listName = (String)dataContext.getData(FavoritesTreeViewPanel.FAVORITES_LIST_NAME);
    e.getPresentation().setEnabled(listName != null && !listName.equals(project.getName()));
  }
}
