package com.intellij.moduleDependencies;

import com.intellij.ide.impl.ContentManagerWatcher;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.peer.PeerFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;

/**
 * User: anna
 * Date: Feb 10, 2005
 */
public class DependenciesAnalyzeManager implements ProjectComponent{
  private Project myProject;
  private ContentManager myContentManager;

  public DependenciesAnalyzeManager(final Project project) {
    myProject = project;
  }

  public static DependenciesAnalyzeManager getInstance(Project project){
    return project.getComponent(DependenciesAnalyzeManager.class);
  }
  public void projectOpened() {
    StartupManager.getInstance(myProject).registerPostStartupActivity(new Runnable() {
      public void run() {
        myContentManager = PeerFactory.getInstance().getContentFactory().createContentManager(true, myProject);
        ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(myProject);
        ToolWindow toolWindow = toolWindowManager.registerToolWindow(ToolWindowId.MODULES_DEPENDENCIES,
                                                                     myContentManager.getComponent(),
                                                                     ToolWindowAnchor.RIGHT);

        toolWindow.setIcon(IconLoader.getIcon("/general/toolWindowInspection.png"));
        new ContentManagerWatcher(toolWindow, myContentManager);
      }
    });
  }

  public void addContent(Content content) {
    myContentManager.addContent(content);
    myContentManager.setSelectedContent(content);
    ToolWindowManager.getInstance(myProject).getToolWindow(ToolWindowId.MODULES_DEPENDENCIES).activate(null);
  }

  public void closeContent(Content content) {
    myContentManager.removeContent(content);
  }

  public void projectClosed() {
    ToolWindowManager.getInstance(myProject).unregisterToolWindow(ToolWindowId.MODULES_DEPENDENCIES);
  }

  public String getComponentName() {
    return "DependenciesAnalyzeManager";
  }

  public void initComponent() {

  }

  public void disposeComponent() {

  }
}
