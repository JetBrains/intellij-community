package com.intellij.ide.favoritesTreeView;

import com.intellij.ide.impl.ContentManagerWatcher;
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
        final Content favoritesContent = PeerFactory.getInstance().getContentFactory().createContent(myFavoritesPanel, "Project " + myProject.getName(), false);
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
  }
}
