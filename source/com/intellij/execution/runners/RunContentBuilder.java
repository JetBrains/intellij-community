/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.execution.runners;

import com.intellij.execution.ExecutionManager;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.configurations.ConfigurationPerRunnerSettings;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.RunnerSettings;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.CloseAction;
import com.intellij.execution.ui.ExecutionConsole;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.Disposeable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Iterator;

/**
 * @author dyoma
 */
public class RunContentBuilder {
  public static final Icon DEFAULT_RERUN_ICON = IconLoader.getIcon("/actions/refreshUsages.png");
  private final JavaProgramRunner myRunner;
  private final Project myProject;
  private final ArrayList<Disposeable> myDisposeables = new ArrayList<Disposeable>();
  private final ArrayList<AnAction> myRunnerActions = new ArrayList<AnAction>();
  private Icon myRerunIcon = DEFAULT_RERUN_ICON;
  private boolean myReuseProhibited = false;
  private ExecutionResult myExecutionResult;
  private JComponent myComponent;
  private RunProfile myRunProfile;
  private RunnerSettings myRunnerSettings;
  private ConfigurationPerRunnerSettings myConfigurationSettings;

  public RunContentBuilder(final Project project, final JavaProgramRunner runner) {
    myProject = project;
    myRunner = runner;
  }

  public ExecutionResult getExecutionResult() {
    return myExecutionResult;
  }

  public void setExecutionResult(final ExecutionResult executionResult) {
    myExecutionResult = executionResult;
  }

  public void setRunProfile(final RunProfile runProfile,
                            RunnerSettings runnerSettings,
                            ConfigurationPerRunnerSettings configurationSettings) {
    myRunProfile = runProfile;
    myRunnerSettings = runnerSettings;
    myConfigurationSettings = configurationSettings;
  }

  public void addAction(final AnAction action) {
    if (action == null) throw new IllegalArgumentException("action");
    myRunnerActions.add(action);
  }

  public RunContentDescriptor createDescriptor() {
    if (myExecutionResult == null) throw new IllegalStateException("Missing ExecutionResult");
    if (myRunProfile == null) throw new IllegalStateException("Missing RunProfile");

    final JPanel panel = new JPanel(new BorderLayout(2, 0));
    panel.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
    final MyRunContentDescriptor contentDescriptor = new MyRunContentDescriptor(myRunProfile, myExecutionResult, myReuseProhibited,  panel, myDisposeables.toArray(new Disposeable[myDisposeables.size()]));

    if(!ApplicationManager.getApplication().isUnitTestMode()) {
      if (myComponent == null) {
        final ExecutionConsole console = myExecutionResult.getExecutionConsole();
        if (console != null) myComponent = console.getComponent();
      }

      if (myComponent != null) panel.add(myComponent, BorderLayout.CENTER);
      panel.add(createActionToolbar(contentDescriptor, panel), BorderLayout.WEST);
    }

    return contentDescriptor;
  }

  private JComponent createActionToolbar(final RunContentDescriptor contentDescriptor, final JComponent component) {
    final DefaultActionGroup actionGroup = new DefaultActionGroup();
    final RestartAction action = new RestartAction(myRunner, myRunProfile, getProcessHandler(), myRerunIcon, contentDescriptor, myRunnerSettings, myConfigurationSettings);
    action.registerShortcut(component);
    actionGroup.add(action);

    final AnAction[] profileActions = myExecutionResult.getActions();
    for (int i = 0; i < profileActions.length; i++) {
      final AnAction profileAction = profileActions[i];
      actionGroup.add(profileAction);
    }

    for (Iterator<AnAction> iterator = myRunnerActions.iterator(); iterator.hasNext();) {
      final AnAction anAction = iterator.next();
      if (anAction != null) actionGroup.add(anAction);
      else actionGroup.addSeparator();
    }

    final AnAction stopAction = ActionManager.getInstance().getAction(IdeActions.ACTION_STOP_PROGRAM);
    actionGroup.add(stopAction);
    actionGroup.add(new CloseAction(myRunner, contentDescriptor, myProject));
    return ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, actionGroup, false).getComponent();
  }

  public ProcessHandler getProcessHandler() {
    return myExecutionResult.getProcessHandler();
  }

  /**
   * @param reuseContent see {@link RunContentDescriptor#myContent}
   */
  public RunContentDescriptor showRunContent(final RunContentDescriptor reuseContent) {
    final RunContentDescriptor descriptor = createDescriptor();
    if(reuseContent != null) descriptor.setAttachedContent(reuseContent.getAttachedContent());
    return descriptor;
  }

  private static class MyRunContentDescriptor extends RunContentDescriptor {
    private final boolean myReuseProhibited;
    private final Disposeable[] myAdditionalDisposables;

    public MyRunContentDescriptor(final RunProfile profile, final ExecutionResult executionResult, final boolean reuseProhibited, final JComponent component, final Disposeable[] additionalDisposables) {
      super(executionResult.getExecutionConsole(), executionResult.getProcessHandler(), component, profile.getName());
      myReuseProhibited = reuseProhibited;
      myAdditionalDisposables = additionalDisposables;
    }

    public boolean isContentReuseProhibited() {
      return myReuseProhibited;
    }

    public void dispose() {
      for (int i = 0; i < myAdditionalDisposables.length; i++) {
        final Disposeable disposable = myAdditionalDisposables[i];
        disposable.dispose();
      }
      super.dispose();
    }
  }
}
