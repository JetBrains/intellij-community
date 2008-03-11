package com.intellij.xdebugger.impl;

import com.intellij.debugger.ui.DebuggerContentInfo;
import com.intellij.execution.DefaultExecutionResult;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.Executor;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.runners.RestartAction;
import com.intellij.execution.ui.CloseAction;
import com.intellij.execution.ui.ExecutionConsole;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.execution.ui.layout.RunnerLayoutUi;
import com.intellij.ide.actions.ContextHelpAction;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.content.Content;
import com.intellij.xdebugger.XDebugProcess;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.impl.actions.XDebuggerActions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author spleaner
 */
public class XDebugSessionTab implements Disposable {

  private static final Icon DEBUG_AGAIN_ICON = IconLoader.getIcon("/actions/startDebugger.png");

  private String mySessionName;
  private Project myProject;
  private RunnerLayoutUi myUi;
  private RunContentDescriptor myRunContentDescriptor;
  private ExecutionConsole myConsole;
  private ExecutionEnvironment myEnvironment;
  private ProgramRunner myRunner;

  public XDebugSessionTab(@NotNull final Project project, @NotNull final String sessionName) {
    myProject = project;
    mySessionName = sessionName;

    myUi = RunnerLayoutUi.Factory.getInstance(project).create("Debug", "unknown!", sessionName, this);
    myUi.initTabDefaults(0, "Debug", null);

    myUi.setTopToolbar(createTopToolbar(), ActionPlaces.DEBUGGER_TOOLBAR);
    myUi.addContent(createFramesContent(), 0, RunnerLayoutUi.PlaceInGrid.left, false);
    myUi.addContent(createVariablesContent(), 0, RunnerLayoutUi.PlaceInGrid.center, false);
  }
  
  private Content createConsoleContent() {
    return myUi.createContent(DebuggerContentInfo.CONSOLE_CONTENT, myConsole.getComponent(), XDebuggerBundle.message("debugger.session.tab.console.content.name"),
                            IconLoader.getIcon("/debugger/console.png"),
                            myConsole.getPreferredFocusableComponent());
  }

  private Content createVariablesContent() {
    final JPanel variablesPanel = new JPanel();
        Content variablesContent = myUi.createContent(DebuggerContentInfo.VARIABLES_CONTENT, variablesPanel, XDebuggerBundle.message("debugger.session.tab.variables.title"),
                                        IconLoader.getIcon("/debugger/value.png"), null);
    return variablesContent;
  }

  private Content createFramesContent() {
    final JPanel framesPanel = new JPanel();
    Content framesContent = myUi.createContent(DebuggerContentInfo.FRAME_CONTENT, framesPanel, XDebuggerBundle.message("debugger.session.tab.frames.title"),
                                    IconLoader.getIcon("/debugger/frame.png"), null);
    final DefaultActionGroup framesGroup = new DefaultActionGroup();

    //addAction(framesGroup, DebuggerActions.POP_FRAME);
    //CommonActionsManager actionsManager = CommonActionsManager.getInstance();
    //framesGroup.add(actionsManager.createPrevOccurenceAction(myFramesPanel.getOccurenceNavigator()));
    //framesGroup.add(actionsManager.createNextOccurenceAction(myFramesPanel.getOccurenceNavigator()));

    //framesContent.setActions(framesGroup, ActionPlaces.DEBUGGER_TOOLBAR, myFramesPanel.getFramesList());
    return framesContent;
  }

  private static void addAction(DefaultActionGroup group, String actionId) {
    group.add(ActionManager.getInstance().getAction(actionId));
  }

  private static DefaultActionGroup createTopToolbar() {
    DefaultActionGroup stepping = new DefaultActionGroup();
    ActionManager actionManager = ActionManager.getInstance();
    stepping.add(actionManager.getAction(XDebuggerActions.SHOW_EXECUTION_POINT));
    stepping.addSeparator();
    stepping.add(actionManager.getAction(XDebuggerActions.STEP_OVER));
    stepping.add(actionManager.getAction(XDebuggerActions.STEP_INTO));
    stepping.add(actionManager.getAction(XDebuggerActions.FORCE_STEP_INTO));
    stepping.add(actionManager.getAction(XDebuggerActions.STEP_OUT));
    stepping.addSeparator();
    stepping.add(actionManager.getAction(XDebuggerActions.RUN_TO_CURSOR));
    return stepping;
  }

  public void dispose() {
    myProject = null;
  }

  public void reuse(@NotNull final XDebugSession session) {
  }

  public String getSessionName() {
    return mySessionName;
  }

  public RunContentDescriptor attachToSession(@NotNull final XDebugSession session, @NotNull final ProgramRunner runner, @NotNull ExecutionEnvironment env) {
    myEnvironment = env;
    myRunner = runner;
    return initUI(getExecutionResult(session));
  }

  @NotNull
  protected ExecutionResult getExecutionResult(@NotNull final XDebugSession session) {
    final XDebugProcess debugProcess = session.getDebugProcess();
    return new DefaultExecutionResult(debugProcess.createConsole(), debugProcess.getProcessHandler());
  }

  private RunContentDescriptor initUI(@NotNull final ExecutionResult executionResult) {
    myConsole = executionResult.getExecutionConsole();

    // attach console here
    myUi.addContent(createConsoleContent(), 1, RunnerLayoutUi.PlaceInGrid.bottom, false);

    myRunContentDescriptor = new RunContentDescriptor(myConsole, executionResult.getProcessHandler(), myUi.getComponent(), getSessionName());

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return myRunContentDescriptor;
    }

    DefaultActionGroup group = new DefaultActionGroup();
    final Executor executor = DefaultDebugExecutor.getDebugExecutorInstance();
    RestartAction restarAction = new RestartAction(executor, myRunner, myRunContentDescriptor.getProcessHandler(), DEBUG_AGAIN_ICON,
                                                   myRunContentDescriptor, myEnvironment);
    group.add(restarAction);
    restarAction.registerShortcut(myUi.getComponent());

    //addActionToGroup(group, DebuggerActions.RESUME);
    //addActionToGroup(group, DebuggerActions.PAUSE);
    addActionToGroup(group, IdeActions.ACTION_STOP_PROGRAM);

    group.addSeparator();

    addActionToGroup(group, XDebuggerActions.VIEW_BREAKPOINTS);
    //addActionToGroup(group, DebuggerActions.MUTE_BREAKPOINTS);

    group.addSeparator();
    //addAction(group, DebuggerActions.EXPORT_THREADS);
    group.addSeparator();

    group.add(myUi.getLayoutActions());

    group.addSeparator();

    group.add(new CloseAction(executor, myRunContentDescriptor, myProject));
    group.add(new ContextHelpAction(executor.getHelpId()));

    myUi.setLeftToolbar(group, ActionPlaces.DEBUGGER_TOOLBAR);

    return myRunContentDescriptor;
  }

  private static void addActionToGroup(final DefaultActionGroup group, final String actionId) {
    AnAction action = ActionManager.getInstance().getAction(actionId);
    if (action != null) group.add(action);
  }

  @Nullable
  public RunContentDescriptor getRunContentDescriptor() {
    return myRunContentDescriptor;
  }
}
