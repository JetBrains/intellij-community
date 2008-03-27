package com.intellij.slicer;

import com.intellij.ide.impl.ContentManagerWatcher;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.ContentManager;
import org.jetbrains.annotations.NotNull;

public class SliceManager implements ProjectComponent {
  private final Project myProject;
  private ContentManager myContentManager;
  private final ToolWindowManager myToolWindowManager;

  public static SliceManager getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, SliceManager.class);
  }

  public SliceManager(Project project, ToolWindowManager toolWindowManager) {
    myProject = project;
    myToolWindowManager = toolWindowManager;
  }

  public void projectOpened() {
    final ToolWindow toolWindow= myToolWindowManager.registerToolWindow("Slice", true, ToolWindowAnchor.BOTTOM );
    myContentManager = toolWindow.getContentManager();
    new ContentManagerWatcher(toolWindow, myContentManager);
  }

  public void projectClosed() {
    myToolWindowManager.unregisterToolWindow("Slice");
  }

  @NotNull
  public String getComponentName() {
    return "SliceManager";
  }

  public void initComponent() {

  }

  public void disposeComponent() {

  }

  public ContentManager getContentManager() {
    return myContentManager;
  }
}
