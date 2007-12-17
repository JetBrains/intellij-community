package com.intellij.xdebugger.impl.breakpoints;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.util.MultiValuesMap;
import com.intellij.util.EventDispatcher;
import com.intellij.util.xmlb.annotations.AbstractCollection;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.xdebugger.breakpoints.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author nik
 */
public class XBreakpointManagerImpl implements XBreakpointManager, PersistentStateComponent<XBreakpointManagerImpl.BreakpointManagerState> {
  private MultiValuesMap<XBreakpointType, XBreakpointBase<?,?>> myBreakpoints = new MultiValuesMap<XBreakpointType, XBreakpointBase<?,?>>();
  private Map<XBreakpointType, EventDispatcher<XBreakpointListener>> myDispatchers = new HashMap<XBreakpointType, EventDispatcher<XBreakpointListener>>();

  @NotNull
  public <T extends XBreakpointProperties> XBreakpoint<T> addBreakpoint(final XBreakpointType<T> type, final @Nullable T properties) {
    XBreakpointBase<T, ?> breakpoint = new XBreakpointBase<T, XBreakpointBase.BreakpointState>(type, properties, new XBreakpointBase.BreakpointState());
    addBreakpoint(breakpoint);
    return breakpoint;
  }

  private <T extends XBreakpointProperties> void addBreakpoint(final XBreakpointBase<T, ?> breakpoint) {
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    XBreakpointType type = breakpoint.getType();
    myBreakpoints.put(type, breakpoint);
    EventDispatcher<XBreakpointListener> dispatcher = myDispatchers.get(type);
    if (dispatcher != null) {
      //noinspection unchecked
      dispatcher.getMulticaster().breakpointAdded(breakpoint);
    }
  }

  public void removeBreakpoint(@NotNull final XBreakpoint<?> breakpoint) {
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    XBreakpointType type = breakpoint.getType();
    myBreakpoints.remove(type, (XBreakpointBase<?,?>)breakpoint);
    EventDispatcher<XBreakpointListener> dispatcher = myDispatchers.get(type);
    if (dispatcher != null) {
      //noinspection unchecked
      dispatcher.getMulticaster().breakpointRemoved(breakpoint);
    }
  }

  @NotNull
  public <T extends XBreakpointProperties> XLineBreakpoint<T> addLineBreakpoint(final XBreakpointType<T> type, @NotNull final String fileUrl,
                                                                            final int line, @Nullable final T properties) {
    XLineBreakpointImpl<T> breakpoint = new XLineBreakpointImpl<T>(type, fileUrl, line, properties);
    addBreakpoint(breakpoint);
    return breakpoint;
  }

  @NotNull
  public XBreakpoint<?>[] getAllBreakpoints() {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    Collection<XBreakpointBase<?,?>> breakpoints = myBreakpoints.values();
    return breakpoints.toArray(new XBreakpoint[breakpoints.size()]);
  }

  @NotNull
  public <T extends XBreakpointProperties> Collection<? extends XBreakpoint<T>> getBreakpoints(@NotNull final XBreakpointType<T> type) {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    Collection<? extends XBreakpointBase<?, ?>> breakpoints = myBreakpoints.get(type);
    if (breakpoints == null) {
      return Collections.emptyList();
    }
    //noinspection unchecked
    return Collections.unmodifiableCollection((Collection<? extends XBreakpoint<T>>)breakpoints);
  }

  private <T extends XBreakpointProperties> EventDispatcher<XBreakpointListener> getOrCreateDispatcher(final XBreakpointType<T> type) {
    EventDispatcher<XBreakpointListener> dispatcher = myDispatchers.get(type);
    if (dispatcher == null) {
      dispatcher = EventDispatcher.create(XBreakpointListener.class);
      myDispatchers.put(type, dispatcher);
    }
    return dispatcher;
  }

  public <T extends XBreakpointProperties> void addBreakpointListener(@NotNull final XBreakpointType<T> type, @NotNull final XBreakpointListener<T> listener) {
    getOrCreateDispatcher(type).addListener(listener);
  }

  public <T extends XBreakpointProperties> void removeBreakpointListener(@NotNull final XBreakpointType<T> type, @NotNull final XBreakpointListener<T> listener) {
    getOrCreateDispatcher(type).removeListener(listener);
  }

  public <T extends XBreakpointProperties> void addBreakpointListener(@NotNull final XBreakpointType<T> type, @NotNull final XBreakpointListener<T> listener,
                                                                      final Disposable parentDisposable) {
    getOrCreateDispatcher(type).addListener(listener, parentDisposable);
  }

  public BreakpointManagerState getState() {
    BreakpointManagerState state = new BreakpointManagerState();
    for (XBreakpointBase<?,?> breakpoint : myBreakpoints.values()) {
      state.getBreakpoints().add(breakpoint.getState());
    }
    return state;
  }

  public void loadState(final BreakpointManagerState state) {
    removeAllBreakpoints();
    for (XBreakpointBase.BreakpointState breakpointState : state.getBreakpoints()) {
      XBreakpointBase<?,?> breakpoint = createBreakpoint(breakpointState);
      if (breakpoint != null) {
        addBreakpoint(breakpoint);
      }
    }
  }

  private void removeAllBreakpoints() {
    for (XBreakpointBase<?, ?> breakpoint : myBreakpoints.values()) {
      removeBreakpoint(breakpoint);
    }
  }

  

  @Nullable
  private static XBreakpointBase<?,?> createBreakpoint(final XBreakpointBase.BreakpointState breakpointState) {
    XBreakpointType<?> type = XBreakpointType.findType(breakpointState.getTypeId());
    if (type == null) return null;                    
    return breakpointState.createBreakpoint(type);
  }


  @Tag("breakpoint-manager")
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
