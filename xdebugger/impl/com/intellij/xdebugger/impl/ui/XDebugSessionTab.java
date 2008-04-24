package com.intellij.xdebugger.impl.ui;

import com.intellij.debugger.ui.DebuggerContentInfo;
import com.intellij.execution.DefaultExecutionResult;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.Executor;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.runners.RestartAction;
import com.intellij.execution.ui.CloseAction;
import com.intellij.execution.ui.ExecutionConsole;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.execution.ui.layout.RunnerLayoutUi;
import com.intellij.ide.CommonActionsManager;
import com.intellij.ide.actions.ContextHelpAction;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.ui.content.Content;
import com.intellij.xdebugger.XDebugProcess;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.impl.XDebugSessionImpl;
import com.intellij.xdebugger.impl.actions.XDebuggerActions;
import com.intellij.xdebugger.impl.frame.XFramesView;
import com.intellij.xdebugger.impl.frame.XVariablesView;
import com.intellij.xdebugger.impl.frame.XWatchesView;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author spleaner
 */
public class XDebugSessionTab implements Disposable {
  private String mySessionName;
  private Project myProject;
  private RunnerLayoutUi myUi;
  private RunContentDescriptor myRunContentDescriptor;
  private ExecutionConsole myConsole;
  private ExecutionEnvironment myEnvironment;
  private ProgramRunner myRunner;
  private XWatchesView myWatchesView;

  public XDebugSessionTab(@NotNull final Project project, @NotNull final String sessionName) {
    myProject = project;
    mySessionName = sessionName;

    myUi = RunnerLayoutUi.Factory.getInstance(project).create("Debug", "unknown!", sessionName, this);
    myUi.getDefaults().initTabDefaults(0, "Debug", null);

    myUi.getOptions().setTopToolbar(createTopToolbar(), ActionPlaces.DEBUGGER_TOOLBAR);
  }
  
  private Content createConsoleContent() {
    return myUi.createContent(DebuggerContentInfo.CONSOLE_CONTENT, myConsole.getComponent(),
                              XDebuggerBundle.message("debugger.session.tab.console.content.name"), XDebuggerUIConstants.CONSOLE_TAB_ICON, 
                              myConsole.getPreferredFocusableComponent());
  }

  private Content createVariablesContent(final XDebugSession session) {
    final XVariablesView variablesView = new XVariablesView(session, this);
    return myUi.createContent(DebuggerContentInfo.VARIABLES_CONTENT, variablesView.getPanel(),
                              XDebuggerBundle.message("debugger.session.tab.variables.title"), XDebuggerUIConstants.VARIABLES_TAB_ICON, null);
  }

  private Content createWatchesContent(final XDebugSession session, final XDebugSessionData sessionData) {
    myWatchesView = new XWatchesView(session, this, sessionData);
    Content watchesContent = myUi.createContent(DebuggerContentInfo.WATCHES_CONTENT, myWatchesView.getMainPanel(),
                                         XDebuggerBundle.message("debugger.session.tab.watches.title"),
                                         XDebuggerUIConstants.WATCHES_TAB_ICON, null);

    ActionGroup group = (ActionGroup)ActionManager.getInstance().getAction(XDebuggerActions.WATCHES_TREE_TOOLBAR_GROUP);
    watchesContent.setActions(group, ActionPlaces.DEBUGGER_TOOLBAR, myWatchesView.getTree());
    return watchesContent;
  }

  private Content createFramesContent(final XDebugSession session) {
    final XFramesView framesView = new XFramesView(session, this);
    Content framesContent = myUi.createContent(DebuggerContentInfo.FRAME_CONTENT, framesView.getMainPanel(),
                                               XDebuggerBundle.message("debugger.session.tab.frames.title"), XDebuggerUIConstants.FRAMES_TAB_ICON, null);
    final DefaultActionGroup framesGroup = new DefaultActionGroup();

    CommonActionsManager actionsManager = CommonActionsManager.getInstance();
    framesGroup.add(actionsManager.createPrevOccurenceAction(framesView.getFramesList()));
    framesGroup.add(actionsManager.createNextOccurenceAction(framesView.getFramesList()));

    framesContent.setActions(framesGroup, ActionPlaces.DEBUGGER_TOOLBAR, framesView.getFramesList());
    return framesContent;
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

  public XDebugSessionData saveData() {
    return new XDebugSessionData(myWatchesView.getWatchExpressions());
  }

  public void dispose() {
    myProject = null;
  }

  public void loadData(final XDebugSessionData sessionData) {
    
  }

  public ExecutionConsole getConsole() {
    return myConsole;
  }

  public String getSessionName() {
    return mySessionName;
  }

  public RunContentDescriptor attachToSession(final @NotNull XDebugSession session, final @NotNull ProgramRunner runner,
                                              final @NotNull ExecutionEnvironment env,
                                              final @NotNull XDebugSessionData sessionData) {
    myEnvironment = env;
    myRunner = runner;
    return initUI(session, sessionData);
  }

  @NotNull
  private static ExecutionResult createExecutionResult(@NotNull final XDebugSession session) {
    final XDebugProcess debugProcess = session.getDebugProcess();
    ProcessHandler processHandler = debugProcess.getProcessHandler();
    processHandler.addProcessListener(new ProcessAdapter() {
      public void processTerminated(final ProcessEvent event) {
        ((XDebugSessionImpl)session).stop();
      }
    });
    return new DefaultExecutionResult(debugProcess.createConsole(), processHandler);
  }

  public XWatchesView getWatchesView() {
    return myWatchesView;
  }

  private RunContentDescriptor initUI(final @NotNull XDebugSession session, final @NotNull XDebugSessionData sessionData) {
    ExecutionResult executionResult = createExecutionResult(session);
    myConsole = executionResult.getExecutionConsole();

    myUi.addContent(createFramesContent(session), 0, RunnerLayoutUi.PlaceInGrid.left, false);
    myUi.addContent(createVariablesContent(session), 0, RunnerLayoutUi.PlaceInGrid.center, false);
    myUi.addContent(createWatchesContent(session, sessionData), 0, RunnerLayoutUi.PlaceInGrid.right, false);
    // attach console here
    myUi.addContent(createConsoleContent(), 1, RunnerLayoutUi.PlaceInGrid.bottom, false);

    myRunContentDescriptor = new RunContentDescriptor(myConsole, executionResult.getProcessHandler(), myUi.getComponent(), getSessionName());

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return myRunContentDescriptor;
    }

    DefaultActionGroup group = new DefaultActionGroup();
    final Executor executor = DefaultDebugExecutor.getDebugExecutorInstance();
    RestartAction restarAction = new RestartAction(executor, myRunner, myRunContentDescriptor.getProcessHandler(), XDebuggerUIConstants.DEBUG_AGAIN_ICON,
                                                   myRunContentDescriptor, myEnvironment);
    group.add(restarAction);
    restarAction.registerShortcut(myUi.getComponent());

    addActionToGroup(group, XDebuggerActions.RESUME);
    addActionToGroup(group, XDebuggerActions.PAUSE);
    addActionToGroup(group, IdeActions.ACTION_STOP_PROGRAM);

    group.addSeparator();

    addActionToGroup(group, XDebuggerActions.VIEW_BREAKPOINTS);
    addActionToGroup(group, XDebuggerActions.MUTE_BREAKPOINTS);

    group.addSeparator();
    //addAction(group, DebuggerActions.EXPORT_THREADS);
    group.addSeparator();

    group.add(myUi.getOptions().getLayoutActions());

    group.addSeparator();

    group.add(new CloseAction(executor, myRunContentDescriptor, myProject));
    group.add(new ContextHelpAction(executor.getHelpId()));

    myUi.getOptions().setLeftToolbar(group, ActionPlaces.DEBUGGER_TOOLBAR);

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
