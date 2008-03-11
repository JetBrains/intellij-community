package com.intellij.xdebugger.impl;

import com.intellij.execution.ExecutionManager;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.execution.ui.RunContentListener;
import com.intellij.execution.ui.RunContentManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.containers.HashMap;
import com.intellij.util.xmlb.annotations.Property;
import com.intellij.xdebugger.*;
import com.intellij.xdebugger.impl.breakpoints.XBreakpointManagerImpl;
import com.intellij.xdebugger.impl.ui.ExecutionPointHighlighter;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author nik
 */
@State(
    name = XDebuggerManagerImpl.COMPONENT_NAME,
    storages = {@Storage(
        id = "other",
        file = "$WORKSPACE_FILE$")})
public class XDebuggerManagerImpl extends XDebuggerManager
    implements ProjectComponent, PersistentStateComponent<XDebuggerManagerImpl.XDebuggerState> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.xdebugger.impl.XDebuggerManagerImpl");
  @NonNls public static final String COMPONENT_NAME = "XDebuggerManager";
  private final Project myProject;
  private final XBreakpointManagerImpl myBreakpointManager;
  private final Map<String, XDebugSessionImpl> myName2Session;
  private ExecutionPointHighlighter myExecutionPointHighlighter;
  private XDebugSessionImpl myLastActiveSession;
  private final Map<String, XDebugSessionTab> mySessionTabs;

  private final RunContentListener myContentListener = new RunContentListener() {
    public void contentSelected(RunContentDescriptor descriptor) {
    }

    public void contentRemoved(RunContentDescriptor descriptor) {
      XDebugSessionTab sessionTab = getSessionTab(descriptor.getDisplayName());
      if (sessionTab != null) {
        mySessionTabs.remove(descriptor.getDisplayName());
        Disposer.dispose(sessionTab);
      }
    }
  };


  public XDebuggerManagerImpl(final Project project, final StartupManager startupManager) {
    myProject = project;
    myBreakpointManager = new XBreakpointManagerImpl(project, this, startupManager);
    myName2Session = new LinkedHashMap<String, XDebugSessionImpl>();
    myExecutionPointHighlighter = new ExecutionPointHighlighter(project);
    mySessionTabs = new HashMap<String, XDebugSessionTab>();
  }

  @NotNull
  public XBreakpointManagerImpl getBreakpointManager() {
    return myBreakpointManager;
  }

  public void projectOpened() {
    RunContentManager contentManager = ExecutionManager.getInstance(myProject).getContentManager();
    LOG.assertTrue(contentManager != null, "Content manager is null");
    contentManager.addRunContentListener(myContentListener, DefaultDebugExecutor.getDebugExecutorInstance());
  }

  public void projectClosed() {
    final RunContentManager contentManager = ExecutionManager.getInstance(myProject).getContentManager();
    contentManager.removeRunContentListener(myContentListener);
  }

  public Project getProject() {
    return myProject;
  }

  @NonNls
  @NotNull
  public String getComponentName() {
    return COMPONENT_NAME;
  }

  public void initComponent() {
  }

  public void disposeComponent() {
    myBreakpointManager.dispose();
  }

  private XDebugSessionTab getSessionTab(@NotNull final String sessionName) {
    return mySessionTabs.get(sessionName);
  }

  @NotNull
  public XDebugSession startSession(@NotNull final ProgramRunner runner,
                                    @NotNull final ExecutionEnvironment env,
                                    @Nullable final RunContentDescriptor contentToReuse,
                                    @NotNull final XDebugProcessStarter processStarter) {
    final RunProfile profile = env.getRunProfile();
    final String sessionName = profile.getName();

    XDebugSessionImpl session = new XDebugSessionImpl(env, runner, this);

    XDebugProcess process = processStarter.start(session);

    final XDebugSessionImpl oldSession = contentToReuse != null ? myName2Session.remove(contentToReuse.getDisplayName()) : null;
    final XDebugSessionTab sessionTab = session.init(process, oldSession);

    myName2Session.put(sessionName, session);
    mySessionTabs.put(sessionName, sessionTab);

    return session;
  }

  public void removeSession(@NotNull XDebugSessionImpl session) {
    myName2Session.remove(session.getSessionName());
    if (myLastActiveSession == session) {
      myLastActiveSession = null;
      onActiveSessionChanged();
    }
  }

  public void updateExecutionPosition(@NotNull XDebugSessionImpl session, @Nullable XSourcePosition position) {
    boolean sessionChanged = myLastActiveSession != session;
    myLastActiveSession = session;
    if (position != null) {
      myExecutionPointHighlighter.show(position);
    }
    else {
      myExecutionPointHighlighter.hide();
    }
    if (sessionChanged) {
      onActiveSessionChanged();
    }
  }

  private void onActiveSessionChanged() {
    myBreakpointManager.getLineBreakpointManager().queueAllBreakpointsUpdate();
  }

  @NotNull
  public XDebugSession[] getDebugSessions() {
    return myName2Session.values().toArray(new XDebugSession[myName2Session.size()]);
  }

  @Nullable
  public XDebugSessionImpl getCurrentSession() {
    if (myLastActiveSession != null) {
      return myLastActiveSession;
    }
    return !myName2Session.isEmpty() ? myName2Session.get(myName2Session.keySet().iterator().next()) : null;
  }

  public XDebuggerState getState() {
    return new XDebuggerState(myBreakpointManager.getState());
  }

  public void loadState(final XDebuggerState state) {
    myBreakpointManager.loadState(state.myBreakpointManagerState);
  }

  public void showExecutionPosition() {
    myExecutionPointHighlighter.navigateTo();
  }

  public static class XDebuggerState {
    private XBreakpointManagerImpl.BreakpointManagerState myBreakpointManagerState;

    public XDebuggerState() {
    }

    public XDebuggerState(final XBreakpointManagerImpl.BreakpointManagerState breakpointManagerState) {
      myBreakpointManagerState = breakpointManagerState;
    }

    @Property(surroundWithTag = false)
    public XBreakpointManagerImpl.BreakpointManagerState getBreakpointManagerState() {
      return myBreakpointManagerState;
    }

    public void setBreakpointManagerState(final XBreakpointManagerImpl.BreakpointManagerState breakpointManagerState) {
      myBreakpointManagerState = breakpointManagerState;
    }
  }

}
