package com.intellij.ide.favoritesTreeView;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.InputValidator;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.IconLoader;

/**
 * User: anna
 * Date: Feb 24, 2005
 */
public class AddNewFavoritesListAction extends AnAction{
  public AddNewFavoritesListAction() {
    super("Add New Favorites List", "Add New Favorites List", IconLoader.getIcon("/general/toolWindowMessages.png"));
  }

  public void actionPerformed(AnActionEvent e) {
    final Project project = (Project)e.getDataContext().getData(DataConstants.PROJECT);
    if (project == null){
      return;
    }
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
    FavoritesViewImpl.getInstance(project).addNewFavoritesList(s);
  }
}
