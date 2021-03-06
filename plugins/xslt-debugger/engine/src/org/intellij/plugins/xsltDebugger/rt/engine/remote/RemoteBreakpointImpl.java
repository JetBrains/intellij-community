/*
 * Copyright 2007 Sascha Weinreuter
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

package org.intellij.plugins.xsltDebugger.rt.engine.remote;

import org.intellij.plugins.xsltDebugger.rt.engine.Breakpoint;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.List;

final class RemoteBreakpointImpl extends UnicastRemoteObject implements RemoteBreakpoint {
  private final Breakpoint myBreakpoint;

  private RemoteBreakpointImpl(Breakpoint breakpoint) throws RemoteException {
    assert breakpoint != null;
    myBreakpoint = breakpoint;
  }

  @Override
  public String getUri() throws RemoteException {
    return myBreakpoint.getUri();
  }

  @Override
  public int getLine() throws RemoteException {
    return myBreakpoint.getLine();
  }

  @Override
  public boolean isEnabled() throws RemoteException {
    return myBreakpoint.isEnabled();
  }

  @Override
  public String getCondition() {
    return myBreakpoint.getCondition();
  }

  @Override
  public String getLogMessage() {
    return myBreakpoint.getLogMessage();
  }

  @Override
  public void setCondition(String expr) {
    myBreakpoint.setCondition(expr);
  }

  @Override
  public void setEnabled(boolean enabled) {
    myBreakpoint.setEnabled(enabled);
  }

  @Override
  public void setLogMessage(String expr) {
    myBreakpoint.setLogMessage(expr);
  }

  @Override
  public String getTraceMessage() throws RemoteException {
    return myBreakpoint.getTraceMessage();
  }

  @Override
  public void setTraceMessage(String expr) throws RemoteException {
    myBreakpoint.setTraceMessage(expr);
  }

  @Override
  public boolean isSuspend() {
    return myBreakpoint.isSuspend();
  }

  @Override
  public void setSuspend(boolean suspend) {
    myBreakpoint.setSuspend(suspend);
  }

  public static List<RemoteBreakpoint> convert(List<? extends Breakpoint> list) throws RemoteException {
    final ArrayList<RemoteBreakpoint> breakpoints = new ArrayList<RemoteBreakpoint>(list.size());
    for (Breakpoint breakpoint : list) {
      breakpoints.add(create(breakpoint));
    }
    return breakpoints;
  }

  public static RemoteBreakpointImpl create(Breakpoint breakpoint) throws RemoteException {
    return breakpoint != null ? new RemoteBreakpointImpl(breakpoint) : null;
  }
}
