/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.openapi.vcs.changes.ui;

import com.intellij.ProjectTopics;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.ModuleAdapter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManagerAdapter;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsListener;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.Disposable;
import com.intellij.peer.PeerFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.content.ContentManagerAdapter;
import com.intellij.ui.content.ContentManagerEvent;
import com.intellij.util.Alarm;
import com.intellij.util.NotNullFunction;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;
import java.util.ArrayList;

/**
 * @author yole
 */
public class ChangesViewContentManager implements ProjectComponent {
  public static final String TOOLWINDOW_ID = VcsBundle.message("changes.toolwindow.name");
  private static final Key<ChangesViewContentEP> myEPKey = Key.create("ChangesViewContentEP");
  private ChangesViewContentManager.MyContentManagerListener myContentManagerListener;

  public static ChangesViewContentManager getInstance(Project project) {
    return project.getComponent(ChangesViewContentManager.class);
  }

  private Project myProject;
  private ContentManager myContentManager;
  private ToolWindow myToolWindow;
  private VcsListener myVcsListener = new MyVcsListener();
  private Alarm myVcsChangeAlarm;
  private final MessageBusConnection myConnection;
  private List<Content> myAddedContents = new ArrayList<Content>();

  public ChangesViewContentManager(final Project project) {
    myProject = project;
    myVcsChangeAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD, project);
    myConnection = project.getMessageBus().connect();
  }

  public void projectOpened() {
    if (ApplicationManager.getApplication().isHeadlessEnvironment()) return;
    StartupManager.getInstance(myProject).registerPostStartupActivity(new Runnable() {
      public void run() {
        final ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(myProject);
        if (toolWindowManager != null) {
          myToolWindow = toolWindowManager.registerToolWindow(TOOLWINDOW_ID, true, ToolWindowAnchor.BOTTOM);
          myToolWindow.setIcon(IconLoader.getIcon("/general/toolWindowChanges.png"));
          updateToolWindowAvailability();
          myContentManager = myToolWindow.getContentManager();
          myContentManagerListener = new MyContentManagerListener();
          myContentManager.addContentManagerListener(myContentManagerListener);
          for(Content content: myAddedContents) {
            myContentManager.addContent(content);
          }
          myAddedContents.clear();
          ProjectLevelVcsManager.getInstance(myProject).addVcsListener(myVcsListener);
          ProjectManager.getInstance().addProjectManagerListener(myProject, new MyProjectManagerListener());
          myConnection.subscribe(ProjectTopics.MODULES, new MyModuleListener());
          loadExtensionTabs();
          if (myContentManager.getContentCount() > 0) {
            myContentManager.setSelectedContent(myContentManager.getContent(0));
          }
        }
      }
    });
  }

  private void loadExtensionTabs() {
    final ChangesViewContentEP[] contentEPs = myProject.getExtensions(ChangesViewContentEP.EP_NAME);
    for(ChangesViewContentEP ep: contentEPs) {
      final NotNullFunction<Project,Boolean> predicate = ep.newPredicateInstance(myProject);
      if (predicate == null || predicate.fun(myProject).equals(Boolean.TRUE)) {
        addExtensionTab(ep);
      }
    }
  }

  private void addExtensionTab(final ChangesViewContentEP ep) {
    final Content content = PeerFactory.getInstance().getContentFactory().createContent(new ContentStub(ep), ep.getTabName(), false);
    content.setCloseable(false);
    content.putUserData(myEPKey, ep);
    myContentManager.addContent(content);
  }

  private void updateExtensionTabs() {
    final ChangesViewContentEP[] contentEPs = myProject.getExtensions(ChangesViewContentEP.EP_NAME);
    for(ChangesViewContentEP ep: contentEPs) {
      final NotNullFunction<Project,Boolean> predicate = ep.newPredicateInstance(myProject);
      if (predicate == null) continue;
      Content epContent = findEPContent(ep);
      final Boolean predicateResult = predicate.fun(myProject);
      if (predicateResult.equals(Boolean.TRUE) && epContent == null) {
        addExtensionTab(ep);
      }
      else if (predicateResult.equals(Boolean.FALSE) && epContent != null) {
        if (!(epContent.getComponent() instanceof ContentStub)) {
          ep.getInstance(myProject).disposeContent();
        }
        myContentManager.removeContent(epContent);
      }
    }
  }

  @Nullable
  private Content findEPContent(final ChangesViewContentEP ep) {
    final Content[] contents = myContentManager.getContents();
    for(Content content: contents) {
      if (content.getUserData(myEPKey) == ep) {
        return content;
      }
    }
    return null;
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
    if (myContentManager == null) {
      myAddedContents.add(content);
    }
    else {
      myContentManager.addContent(content);
    }
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

  @Nullable
  public Content getSelectedContent() {
    return myContentManager.getSelectedContent();
  }

  @Nullable
  public <T> T getActiveComponent(final Class<T> aClass) {
    final Content content = myContentManager.getSelectedContent();
    if (content != null && aClass.isInstance(content.getComponent())) {
      //noinspection unchecked
      return (T) content.getComponent();
    }
    return null;
  }

  private class MyVcsListener implements VcsListener {
    public void directoryMappingChanged() {
      myVcsChangeAlarm.cancelAllRequests();
      myVcsChangeAlarm.addRequest(new Runnable() {
        public void run() {
          updateToolWindowAvailability();
          updateExtensionTabs();
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

  private static class ContentStub extends JPanel {
    private ChangesViewContentEP myEP;

    public ContentStub(final ChangesViewContentEP EP) {
      myEP = EP;
    }

    public ChangesViewContentEP getEP() {
      return myEP;
    }
  }

  private class MyContentManagerListener extends ContentManagerAdapter {
    public void selectionChanged(final ContentManagerEvent event) {
      if (event.getContent().getComponent() instanceof ContentStub) {
        ChangesViewContentEP ep = ((ContentStub) event.getContent().getComponent()).getEP();
        ChangesViewContentProvider provider = ep.getInstance(myProject);
        final JComponent contentComponent = provider.initContent();
        event.getContent().setComponent(contentComponent);
        if (contentComponent instanceof Disposable) {
          event.getContent().setDisposer((Disposable) contentComponent);          
        }
      }
    }
  }

  private class MyProjectManagerListener extends ProjectManagerAdapter {
    public void projectClosing(final Project project) {
      if (myContentManager != null) {
        myContentManager.removeContentManagerListener(myContentManagerListener);
      }
      ProjectManager.getInstance().removeProjectManagerListener(project, this);
    }
  }
}
