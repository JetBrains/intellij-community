/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.debugger;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.breakpoints.XBreakpointHandler;
import com.intellij.xdebugger.breakpoints.XBreakpointProperties;
import com.intellij.xdebugger.breakpoints.XBreakpointType;
import com.intellij.xdebugger.breakpoints.XLineBreakpoint;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

/**
 * @author traff
 */
public class AbstractLineBreakpointHandler extends XBreakpointHandler<XLineBreakpoint<XBreakpointProperties>> {
  protected final PyDebugProcess myDebugProcess;
  private final Map<XLineBreakpoint<XBreakpointProperties>, XSourcePosition> myBreakPointPositions = Maps.newHashMap();

  public AbstractLineBreakpointHandler(
    Class<? extends XBreakpointType<XLineBreakpoint<XBreakpointProperties>, ?>> breakpointTypeClass,
    @NotNull final PyDebugProcess debugProcess) {
    super(breakpointTypeClass);
    myDebugProcess = debugProcess;
  }

  public void reregisterBreakpoints() {
    List<XLineBreakpoint<XBreakpointProperties>> breakpoints = Lists.newArrayList(myBreakPointPositions.keySet());
    for (XLineBreakpoint<XBreakpointProperties> breakpoint : breakpoints) {
      unregisterBreakpoint(breakpoint, false);
      registerBreakpoint(breakpoint);
    }
  }

  public void registerBreakpoint(@NotNull final XLineBreakpoint<XBreakpointProperties> breakpoint) {
    final XSourcePosition position = breakpoint.getSourcePosition();
    if (position != null) {
      myDebugProcess.addBreakpoint(myDebugProcess.getPositionConverter().convertToPython(position), breakpoint);
      myBreakPointPositions.put(breakpoint, position);
    }
  }

  public void unregisterBreakpoint(@NotNull final XLineBreakpoint<XBreakpointProperties> breakpoint, final boolean temporary) {
    final XSourcePosition position = myBreakPointPositions.get(breakpoint);
    if (position != null) {
      myDebugProcess.removeBreakpoint(myDebugProcess.getPositionConverter().convertToPython(position));
      myBreakPointPositions.remove(breakpoint);
    }
  }
}
