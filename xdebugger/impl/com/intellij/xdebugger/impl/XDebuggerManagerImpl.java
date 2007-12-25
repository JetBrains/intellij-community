package com.intellij.xdebugger.impl;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.util.xmlb.annotations.Property;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.impl.breakpoints.XBreakpointManagerImpl;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

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
  private Project myProject;
  private XBreakpointManagerImpl myBreakpointManager;

  public XDebuggerManagerImpl(final Project project, final StartupManager startupManager) {
    myProject = project;
    myBreakpointManager = new XBreakpointManagerImpl(myProject, startupManager);
  }

  @NotNull
  public XBreakpointManagerImpl getBreakpointManager() {
    return myBreakpointManager;
  }

  public void projectOpened() {
  }

  public void projectClosed() {
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

  public XDebuggerState getState() {
    return new XDebuggerState(myBreakpointManager.getState());
  }

  public void loadState(final XDebuggerState state) {
    myBreakpointManager.loadState(state.myBreakpointManagerState);
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
