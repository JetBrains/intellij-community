
package com.intellij.ui.content.impl;

import com.intellij.ide.impl.ContentManagerWatcher;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.MessageView;
import com.intellij.ui.content.TabbedPaneContentUI;

/**
 * @author Eugene Belyaev
 */
public class MessageViewImpl extends ContentManagerImpl implements ProjectComponent, MessageView {
  private Project myProject;

  public MessageViewImpl(Project project) {
    super(new TabbedPaneContentUI(), true, project);
    myProject = project;
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }

  public void projectOpened() {
    ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(myProject);
    ToolWindow toolWindow=toolWindowManager.registerToolWindow(ToolWindowId.MESSAGES_WINDOW,getComponent(),ToolWindowAnchor.BOTTOM);
    toolWindow.setIcon(IconLoader.getIcon("/general/toolWindowMessages.png"));
    new ContentManagerWatcher(toolWindow,this);
  }

  public void projectClosed() {
    ToolWindowManager.getInstance(myProject).unregisterToolWindow(ToolWindowId.MESSAGES_WINDOW);
  }

  public String getComponentName() {
    return "MessageViewImpl";
  }
}
