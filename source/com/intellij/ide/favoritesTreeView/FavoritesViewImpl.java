package com.intellij.ide.favoritesTreeView;

import com.intellij.ide.impl.ContentManagerWatcher;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.peer.PeerFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.TabbedPaneContentUI;
import com.intellij.ui.content.impl.ContentManagerImpl;

public class FavoritesViewImpl extends ContentManagerImpl implements ProjectComponent {
  private Project myProject;
  private FavoritesTreeViewPanel myFavoritesPanel;

  public static FavoritesViewImpl getInstance(Project project) {
    return project.getComponent(FavoritesViewImpl.class);
  }

  public FavoritesViewImpl(Project project, ProjectManager projectManager) {
    super(new TabbedPaneContentUI(), true, project, projectManager);
    myProject = project;
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }

  public void projectOpened() {
    if (!((ApplicationEx)ApplicationManager.getApplication()).isInternal()) return;
    StartupManager.getInstance(myProject).registerPostStartupActivity(new Runnable() {
      public void run() {
        ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(myProject);
        ToolWindow toolWindow = toolWindowManager.registerToolWindow(ToolWindowId.FAVORITES_VIEW, getComponent(), ToolWindowAnchor.RIGHT);
        toolWindow.setIcon(IconLoader.getIcon("/general/toolWindowMessages.png"));
        new ContentManagerWatcher(toolWindow, FavoritesViewImpl.this);
        myFavoritesPanel = new FavoritesTreeViewPanel(myProject, null);
        final Content favoritesContent = PeerFactory.getInstance().getContentFactory().createContent(myFavoritesPanel, myProject.getName(), false);
        addContent(favoritesContent);
      }
    });
  }

  public FavoritesTreeViewPanel getFavoritesTreeViewPanel() {
    return myFavoritesPanel;
  }

  public void projectClosed() {
    if (((ApplicationEx)ApplicationManager.getApplication()).isInternal()){
      ToolWindowManager.getInstance(myProject).unregisterToolWindow(ToolWindowId.FAVORITES_VIEW);
    }
  }

  public String getComponentName() {
    return "FavoritesViewImpl";
  }          /**
 * User: anna
 * Date: Feb 23, 2005
 */
public class RemoveFromFavoritesAction extends AnAction {
  public RemoveFromFavoritesAction() {
    super("Remove From Favorites", "Remove Selected Favorite", IconLoader.getIcon("/actions/cancel.png"));
  }

  public void actionPerformed(AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    Project project = (Project)dataContext.getData(DataConstants.PROJECT);
    if (project == null) {
      return;
    }
    if (e.getPlace() == ActionPlaces.FAVORITES_VIEW_POPUP) {
      FavoritesTreeNodeDescriptor[] selectedNodeDescriptors = FavoritesViewImpl.getInstance(project).getFavoritesTreeViewPanel()
          .getSelectedNodeDescriptors();
      for (int i = 0; i < selectedNodeDescriptors.length; i++) {
        FavoritesTreeNodeDescriptor selectedNodeDescriptor = selectedNodeDescriptors[i];
        selectedNodeDescriptor = getFavoritesRoot(selectedNodeDescriptor, project);
        if (selectedNodeDescriptor != null) {
          FavoritesViewImpl.getInstance(project).getFavoritesTreeViewPanel().removeFromFavorites((AbstractTreeNode)selectedNodeDescriptor.getElement());
        }
      }
    }
  }

  private FavoritesTreeNodeDescriptor getFavoritesRoot(FavoritesTreeNodeDescriptor node, Project project) {
    while (node.getParentDescriptor() != null && node.getParentDescriptor() instanceof FavoritesTreeNodeDescriptor) {
      FavoritesTreeNodeDescriptor favoritesDescriptor = (FavoritesTreeNodeDescriptor)node.getParentDescriptor();
      if (favoritesDescriptor.getElement() == FavoritesTreeStructure.getInstance(project).getRootElement()) {
        return node;
      }
      node = favoritesDescriptor;
    }
    return node;
  }

  public void update(AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    Project project = (Project)dataContext.getData(DataConstants.PROJECT);
    e.getPresentation()
      .setEnabled(project != null &&
                                  FavoritesViewImpl.getInstance(project).getFavoritesTreeViewPanel()
                                    .getSelectedNodeDescriptors() != null);
  }


}
}
