package com.intellij.xdebugger.impl;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.annotations.Tag;
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

  public XDebuggerManagerImpl(final Project project) {
    myProject = project;
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
  }

  public XDebuggerState getState() {
    return new XDebuggerState(myBreakpointManager.getState());
  }

  public void loadState(final XDebuggerState state) {
    myBreakpointManager.loadState(state.myBreakpointManagerState);
  }

  public static class XDebuggerState {
    private XBreakpointManagerImpl.BreakpointManagerState myBreakpointManagerState;

    public XDebuggerState(final XBreakpointManagerImpl.BreakpointManagerState breakpointManagerState) {
      myBreakpointManagerState = breakpointManagerState;
    }

    @Tag("breakpoint-manager")
    public XBreakpointManagerImpl.BreakpointManagerState getBreakpointManagerState() {
      return myBreakpointManagerState;
    }

    public void setBreakpointManagerState(final XBreakpointManagerImpl.BreakpointManagerState breakpointManagerState) {
      myBreakpointManagerState = breakpointManagerState;
    }
  }
}
