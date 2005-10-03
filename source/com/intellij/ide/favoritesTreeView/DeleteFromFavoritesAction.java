package com.intellij.ide.favoritesTreeView;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ide.util.treeView.AbstractTreeNode;
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
    final FavoritesTreeViewPanel currentTreeViewPanel = FavoritesViewImpl.getInstance(project).getCurrentTreeViewPanel();
    FavoritesTreeNodeDescriptor[] selectedNodeDescriptors = currentTreeViewPanel
        .getSelectedNodeDescriptors();
    removeNodes(selectedNodeDescriptors, project, currentTreeViewPanel.getName());
  }

  public static void removeNodes(final FavoritesTreeNodeDescriptor[] selectedNodeDescriptors, final Project project, String favoritesViewPane) {
    final FavoritesTreeViewPanel favoritesTreeViewPanel = FavoritesViewImpl.getInstance(project).getFavoritesTreeViewPanel(favoritesViewPane);
    if (selectedNodeDescriptors != null) {
      for (FavoritesTreeNodeDescriptor selectedNodeDescriptor : selectedNodeDescriptors) {
        selectedNodeDescriptor = FavoritesTreeNodeDescriptor.getFavoritesRoot(selectedNodeDescriptor, project, favoritesViewPane);
        if (selectedNodeDescriptor != null) {
          favoritesTreeViewPanel.removeFromFavorites(selectedNodeDescriptor.getElement());
        }
      }
    }
  }



  public void update(AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    Project project = (Project)dataContext.getData(DataConstants.PROJECT);
    if (project == null){
      e.getPresentation().setEnabled(false);
      return;
    }
    final FavoritesTreeNodeDescriptor[] selectedNodeDescriptors = FavoritesViewImpl.getInstance(project).getCurrentTreeViewPanel()
        .getSelectedNodeDescriptors();
    if (selectedNodeDescriptors == null || selectedNodeDescriptors.length == 0){
      e.getPresentation().setEnabled(false);
    } else {
      final AbstractTreeNode node = selectedNodeDescriptors[0].getElement();
      e.getPresentation().setEnabled(node != null && !(node.getValue() instanceof String));
    }
  }


}
