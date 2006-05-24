package com.intellij.ide.favoritesTreeView;

import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;

import java.util.Collections;

/**
 * User: anna
 * Date: Feb 24, 2005
 */
public class SendToFavoritesAction extends AnAction{
  private String toName;
  public SendToFavoritesAction(String name) {
    super(name);
    toName = name;
  }

  public void actionPerformed(AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    Project project = (Project)dataContext.getData(DataConstants.PROJECT);
    final FavoritesManager favoritesManager = FavoritesManager.getInstance(project);

    FavoritesTreeNodeDescriptor[] roots = (FavoritesTreeNodeDescriptor[])dataContext.getData(FavoritesTreeViewPanel.CONTEXT_FAVORITES_ROOTS);
    String listName = (String)dataContext.getData(FavoritesTreeViewPanel.FAVORITES_LIST_NAME);

    doSend(favoritesManager, roots, listName);
  }

  public void doSend(final FavoritesManager favoritesManager, final FavoritesTreeNodeDescriptor[] roots, final String listName) {
    for (FavoritesTreeNodeDescriptor root : roots) {
      final AbstractTreeNode rootElement = root.getElement();
      favoritesManager.removeRoot(listName, rootElement.getValue());
      favoritesManager.addRoots(toName, Collections.singletonList(rootElement));
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
    if (roots == null || roots.length == 0) {
      e.getPresentation().setEnabled(false);
      return;
    }

    String listName = (String)dataContext.getData(FavoritesTreeViewPanel.FAVORITES_LIST_NAME);
    e.getPresentation().setEnabled(listName != null);
  }
}
