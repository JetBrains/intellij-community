package com.intellij.xdebugger.impl.breakpoints;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.util.xmlb.annotations.AbstractCollection;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.XBreakpointManager;
import com.intellij.xdebugger.breakpoints.XBreakpointProperties;
import com.intellij.xdebugger.breakpoints.XBreakpointType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author nik
 */
public class XBreakpointManagerImpl implements XBreakpointManager, PersistentStateComponent<XBreakpointManagerImpl.BreakpointManagerState> {
  private List<XBreakpointImpl<?>> myBreakpoints = new ArrayList<XBreakpointImpl<?>>();

  public <T extends XBreakpointProperties> XBreakpoint<T> addBreakpoint(final XBreakpointType<T> type, final T properties) {
    XBreakpointImpl<T> breakpoint = new XBreakpointImpl<T>(type, properties);
    myBreakpoints.add(breakpoint);
    return breakpoint;
  }

  public void removeBreakpoint(final XBreakpoint<?> breakpoint) {
    //noinspection SuspiciousMethodCalls
    myBreakpoints.remove(breakpoint);
  }

  public XBreakpoint[] getBreakpoints() {
    return myBreakpoints.toArray(new XBreakpoint[myBreakpoints.size()]);
  }

  public BreakpointManagerState getState() {
    BreakpointManagerState state = new BreakpointManagerState();
    for (XBreakpointImpl breakpoint : myBreakpoints) {
      state.getBreakpoints().add(breakpoint.getState());
    }
    return state;
  }

  public void loadState(final BreakpointManagerState state) {
    myBreakpoints.clear();
    for (XBreakpointImpl.BreakpointState breakpointState : state.getBreakpoints()) {
      XBreakpointImpl breakpoint = createBreakpoint(breakpointState);
      if (breakpoint != null) {
        myBreakpoints.add(breakpoint);
      }
    }
  }

  @Nullable
  private static XBreakpointImpl createBreakpoint(final XBreakpointImpl.BreakpointState breakpointState) {
    XBreakpointType type = XBreakpointType.findType(breakpointState.getTypeId());
    if (type == null) return null;
    return new XBreakpointImpl(type, breakpointState);
  }

  public static class BreakpointManagerState {
    private List<XBreakpointImpl.BreakpointState> myBreakpoints = new ArrayList<XBreakpointImpl.BreakpointState>();

    @Tag("breakpoints")
    @AbstractCollection(surroundWithTag = false)
    public List<XBreakpointImpl.BreakpointState> getBreakpoints() {
      return myBreakpoints;
    }

    public void setBreakpoints(final List<XBreakpointImpl.BreakpointState> breakpoints) {
      myBreakpoints = breakpoints;
    }
  }
}
