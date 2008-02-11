package com.intellij.xdebugger.impl;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.util.xmlb.annotations.Property;
import com.intellij.xdebugger.*;
import com.intellij.xdebugger.impl.breakpoints.XBreakpointManagerImpl;
import com.intellij.xdebugger.impl.ui.ExecutionPointHighlighter;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author nik
 */
@State(
  name = XDebuggerManagerImpl.COMPONENT_NAME,
  storages = {
    @Storage(
      id="other",
      file = "$WORKSPACE_FILE$"
    )
  }
)
public class XDebuggerManagerImpl extends XDebuggerManager implements ProjectComponent, PersistentStateComponent<XDebuggerManagerImpl.XDebuggerState> {
  @NonNls public static final String COMPONENT_NAME = "XDebuggerManager";
  private final Project myProject;
  private final XBreakpointManagerImpl myBreakpointManager;
  private final List<XDebugSessionImpl> mySessions;
  private ExecutionPointHighlighter myExecutionPointHighlighter;
  private XDebugSessionImpl myLastActiveSession;

  public XDebuggerManagerImpl(final Project project, final StartupManager startupManager) {
    myProject = project;
    myBreakpointManager = new XBreakpointManagerImpl(project, this, startupManager);
    mySessions = new ArrayList<XDebugSessionImpl>();
    myExecutionPointHighlighter = new ExecutionPointHighlighter(project);
  }

  @NotNull
  public XBreakpointManagerImpl getBreakpointManager() {
    return myBreakpointManager;
  }

  public void projectOpened() {
  }

  public void projectClosed() {
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


  @NotNull
  public XDebugSessionImpl startSession(@NotNull XDebugProcessStarter processStarter) {
    XDebugSessionImpl session = new XDebugSessionImpl(this);
    XDebugProcess process = processStarter.start(session);
    session.init(process);
    mySessions.add(session);
    return session;
  }

  public void removeSession(@NotNull XDebugSessionImpl session) {
    mySessions.remove(session);
    if (myLastActiveSession == session) {
      myLastActiveSession = null;
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
    return mySessions.toArray(new XDebugSession[mySessions.size()]);
  }

  @Nullable
  public XDebugSessionImpl getCurrentSession() {
    if (myLastActiveSession != null) {
      return myLastActiveSession;
    }
    return !mySessions.isEmpty() ? mySessions.get(0) : null;
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
