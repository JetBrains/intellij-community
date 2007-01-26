/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

/*
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 05.12.2006
 * Time: 17:42:54
 */
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.ProjectTopics;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.ModuleAdapter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsListener;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.peer.PeerFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.util.Alarm;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ChangesViewContentManager implements ProjectComponent {
  public static final String TOOLWINDOW_ID = VcsBundle.message("changes.toolwindow.name");

  public static ChangesViewContentManager getInstance(Project project) {
    return project.getComponent(ChangesViewContentManager.class);
  }

  private Project myProject;
  private ContentManager myContentManager;
  private ToolWindow myToolWindow;
  private VcsListener myVcsListener = new MyVcsListener();
  private Alarm myVcsChangeAlarm;
  private final MessageBusConnection myConnection;

  public ChangesViewContentManager(final Project project) {
    myProject = project;
    myVcsChangeAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD, project);
    myConnection = project.getMessageBus().connect();
    myContentManager = PeerFactory.getInstance().getContentFactory().createContentManager(true, myProject);
  }

  public void projectOpened() {
    if (ApplicationManager.getApplication().isHeadlessEnvironment()) return;
    StartupManager.getInstance(myProject).registerPostStartupActivity(new Runnable() {
      public void run() {
        final ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(myProject);
        if (toolWindowManager != null) {
          myToolWindow = toolWindowManager.registerToolWindow(TOOLWINDOW_ID, myContentManager.getComponent(), ToolWindowAnchor.BOTTOM);
          myToolWindow.setIcon(IconLoader.getIcon("/general/toolWindowChanges.png"));
          updateToolWindowAvailability();
          ProjectLevelVcsManager.getInstance(myProject).addVcsListener(myVcsListener);
          myConnection.subscribe(ProjectTopics.MODULES, new MyModuleListener());
        }
      }
    });
  }

  private void updateToolWindowAvailability() {
    final AbstractVcs[] abstractVcses = ProjectLevelVcsManager.getInstance(myProject).getAllActiveVcss();
    myToolWindow.setAvailable(abstractVcses.length > 0, null);
  }

  public void projectClosed() {
    ProjectLevelVcsManager.getInstance(myProject).removeVcsListener(myVcsListener);
    myVcsChangeAlarm.cancelAllRequests();
    myConnection.disconnect();
    if (ApplicationManager.getApplication().isHeadlessEnvironment()) return;
    if (myToolWindow != null) {
      ToolWindowManager.getInstance(myProject).unregisterToolWindow(TOOLWINDOW_ID);
    }
  }

  @NonNls @NotNull
  public String getComponentName() {
    return "ChangesViewContentManager";
  }

  public void addContent(Content content) {
    myContentManager.addContent(content);
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }

  public void removeContent(final Content content) {
    myContentManager.removeContent(content);
  }

  public void setSelectedContent(final Content content) {
    myContentManager.setSelectedContent(content);
  }

  private class MyVcsListener implements VcsListener {
    public void directoryMappingChanged() {
      myVcsChangeAlarm.cancelAllRequests();
      myVcsChangeAlarm.addRequest(new Runnable() {
        public void run() {
          updateToolWindowAvailability();
        }
      }, 100, ModalityState.NON_MODAL);
    }
  }

  private class MyModuleListener extends ModuleAdapter {
    public void moduleAdded(Project project, Module module) {
      updateToolWindowAvailability();
    }

    public void moduleRemoved(Project project, Module module) {
      updateToolWindowAvailability();
    }
  }
}
