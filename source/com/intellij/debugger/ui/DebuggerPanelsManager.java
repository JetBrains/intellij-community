package com.intellij.debugger.ui;

import com.intellij.debugger.DebuggerInvocationUtil;
import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.impl.*;
import com.intellij.debugger.ui.impl.MainWatchPanel;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionManager;
import com.intellij.execution.configurations.RemoteConnection;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.JavaProgramRunner;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.execution.ui.RunContentListener;
import com.intellij.execution.ui.RunContentManager;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;

import java.util.HashMap;

/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */

public class DebuggerPanelsManager implements ProjectComponent{
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.ui.DebuggerPanelsManager");

  private final Project myProject;

  private PositionHighlighter myEditorManager;
  private HashMap<ProcessHandler, DebuggerSessionTab> mySessionTabs = new HashMap<ProcessHandler, DebuggerSessionTab>();

  public DebuggerPanelsManager(Project project) {
    myProject = project;
  }

  private DebuggerStateManager getContextManager() {
    return DebuggerManagerEx.getInstanceEx(myProject).getContextManager();
  }

  private final RunContentListener myContentListener = new RunContentListener() {
    public void contentSelected(RunContentDescriptor descriptor) {
      DebuggerSessionTab sessionTab = descriptor != null ? getSessionTab(descriptor.getProcessHandler()) : null;

      if (sessionTab != null) {
        getContextManager().setState(sessionTab.getContextManager().getContext(), sessionTab.getSession().getState(), DebuggerSession.EVENT_CONTEXT, null);
      }
      else {
        getContextManager().setState(DebuggerContextImpl.EMPTY_CONTEXT, DebuggerSession.STATE_DISPOSED, DebuggerSession.EVENT_CONTEXT, null);
      }
    }

    public void contentRemoved(RunContentDescriptor descriptor) {
      DebuggerSessionTab sessionTab = getSessionTab(descriptor.getProcessHandler());
      if (sessionTab != null) {
        mySessionTabs.remove(descriptor.getProcessHandler());
        sessionTab.dispose();
      }
    }
  };

  public RunContentDescriptor attachVirtualMachine(RunProfile runProfile,
                                              JavaProgramRunner runner,
                                              RunProfileState state,
                                              RunContentDescriptor reuseContent,
                                              RemoteConnection remoteConnection,
                                              boolean pollConnection) throws ExecutionException {
    DebuggerSession debuggerSession = DebuggerManagerEx.getInstanceEx(myProject).attachVirtualMachine(runProfile.getName(), state, remoteConnection, pollConnection);

    DebuggerSessionTab sessionTab = new DebuggerSessionTab(myProject);
    RunContentDescriptor runContentDescriptor = sessionTab.attachToSession(
            debuggerSession,
            runner,
            runProfile,
            state.getRunnerSettings(),
            state.getConfigurationSettings());

    mySessionTabs.put(runContentDescriptor.getProcessHandler(), sessionTab);
    return runContentDescriptor;
  }


  public void projectOpened() {
    myEditorManager = new PositionHighlighter(myProject, getContextManager());
    getContextManager().addListener(new DebuggerContextListener() {
      public void changeEvent(final DebuggerContextImpl newContext, int event) {
        if(event == DebuggerSession.EVENT_PAUSE) {
          DebuggerInvocationUtil.invokeLater(myProject, new Runnable() {
            public void run() {
              toFront(newContext.getDebuggerSession());
            }
          });
        }
      }
    });

    RunContentManager contentManager = ExecutionManager.getInstance(myProject).getContentManager();
    LOG.assertTrue(contentManager != null, "Content manager is null");
    contentManager.addRunContentListener(myContentListener, GenericDebuggerRunner.getRunnerInfo());
  }

  public void projectClosed() {
    ExecutionManager.getInstance(myProject).getContentManager().removeRunContentListener(myContentListener);
  }

  public String getComponentName() {
    return "DebuggerPanelsManager";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }

  public static DebuggerPanelsManager getInstance(Project project) {
    return project.getComponent(DebuggerPanelsManager.class);
  }

  public MainWatchPanel getWatchPanel() {
    DebuggerContextImpl context = DebuggerManagerEx.getInstanceEx(myProject).getContext();
    DebuggerSessionTab sessionTab = getSessionTab(context.getDebuggerSession());
    return sessionTab != null ? sessionTab.getWatchPanel() : null;
  }

  public void showFramePanel() {
    DebuggerContextImpl context = DebuggerManagerEx.getInstanceEx(myProject).getContext();
    DebuggerSessionTab sessionTab = getSessionTab(context.getDebuggerSession());
    if(sessionTab != null) {
      sessionTab.showFramePanel();
    }
  }

  public void toFront(DebuggerSession session) {
    DebuggerSessionTab sessionTab = getSessionTab(session);
    if(sessionTab != null) {
      sessionTab.toFront();
    }
  }

  private DebuggerSessionTab getSessionTab(ProcessHandler processHandler) {
    return mySessionTabs.get(processHandler);
  }

  private DebuggerSessionTab getSessionTab(DebuggerSession session) {
    return session != null ? getSessionTab(session.getProcess().getExecutionResult().getProcessHandler()) : null;
  }

  public void updateContextPointDescription() {
    myEditorManager.updateContextPointDescription();
  }
}
