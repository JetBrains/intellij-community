package com.intellij.xdebugger.impl.breakpoints;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.util.xmlb.annotations.AbstractCollection;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.xdebugger.breakpoints.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author nik
 */
public class XBreakpointManagerImpl implements XBreakpointManager, PersistentStateComponent<XBreakpointManagerImpl.BreakpointManagerState> {
  private List<XBreakpointBase<?,?>> myBreakpoints = new ArrayList<XBreakpointBase<?,?>>();

  @NotNull
  public <T extends XBreakpointProperties> XBreakpoint<T> addBreakpoint(final XBreakpointType<T> type, final @Nullable T properties) {
    XBreakpointBase<T, ?> breakpoint = new XBreakpointBase<T, XBreakpointBase.BreakpointState>(type, properties, new XBreakpointBase.BreakpointState());
    myBreakpoints.add(breakpoint);
    return breakpoint;
  }

  @NotNull
  public <T extends XBreakpointProperties> XLineBreakpoint<T> addLineBreakpoint(final XBreakpointType<T> type, @NotNull final String fileUrl,
                                                                            final int line, @Nullable final T properties) {
    XLineBreakpointImpl<T> breakpoint = new XLineBreakpointImpl<T>(type, fileUrl, line, properties);
    myBreakpoints.add(breakpoint);
    return breakpoint;
  }

  public void removeBreakpoint(@NotNull final XBreakpoint<?> breakpoint) {
    //noinspection SuspiciousMethodCalls
    myBreakpoints.remove(breakpoint);
  }

  @NotNull
  public XBreakpoint[] getBreakpoints() {
    return myBreakpoints.toArray(new XBreakpoint[myBreakpoints.size()]);
  }

  public BreakpointManagerState getState() {
    BreakpointManagerState state = new BreakpointManagerState();
    for (XBreakpointBase breakpoint : myBreakpoints) {
      state.getBreakpoints().add(breakpoint.getState());
    }
    return state;
  }

  public void loadState(final BreakpointManagerState state) {
    myBreakpoints.clear();
    for (XBreakpointBase.BreakpointState breakpointState : state.getBreakpoints()) {
      XBreakpointBase breakpoint = createBreakpoint(breakpointState);
      if (breakpoint != null) {
        myBreakpoints.add(breakpoint);
      }
    }
  }

  @Nullable
  private static XBreakpointBase<?,?> createBreakpoint(final XBreakpointBase.BreakpointState breakpointState) {
    XBreakpointType<?> type = XBreakpointType.findType(breakpointState.getTypeId());
    if (type == null) return null;                    
    return breakpointState.createBreakpoint(type);
  }


  public static class BreakpointManagerState {
    private List<XBreakpointBase.BreakpointState> myBreakpoints = new ArrayList<XBreakpointBase.BreakpointState>();

    @Tag("breakpoints")
    @AbstractCollection(surroundWithTag = false, elementTypes = {XBreakpointBase.BreakpointState.class, XLineBreakpointImpl.LineBreakpointState.class})
    public List<XBreakpointBase.BreakpointState> getBreakpoints() {
      return myBreakpoints;
    }

    public void setBreakpoints(final List<XBreakpointBase.BreakpointState> breakpoints) {
      myBreakpoints = breakpoints;
    }
  }
}
