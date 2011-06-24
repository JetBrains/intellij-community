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

import javax.rmi.PortableRemoteObject;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: sweinreuter
 * Date: 29.05.2007
 */
class RemoteBreakpointImpl extends PortableRemoteObject implements RemoteBreakpoint {
  private final Breakpoint myBreakpoint;

  private RemoteBreakpointImpl(Breakpoint breakpoint) throws RemoteException {
    assert breakpoint != null;
    myBreakpoint = breakpoint;
  }

  public String getUri() throws RemoteException {
    return myBreakpoint.getUri();
  }

  public int getLine() throws RemoteException {
    return myBreakpoint.getLine();
  }

  public boolean isEnabled() throws RemoteException {
    return myBreakpoint.isEnabled();
  }

  public String getCondition() {
    return myBreakpoint.getCondition();
  }

  public String getLogMessage() {
    return myBreakpoint.getLogMessage();
  }

  public void setCondition(String expr) {
    myBreakpoint.setCondition(expr);
  }

  public void setEnabled(boolean enabled) {
    myBreakpoint.setEnabled(enabled);
  }

  public void setLogMessage(String expr) {
    myBreakpoint.setLogMessage(expr);
  }

  public String getTraceMessage() throws RemoteException {
    return myBreakpoint.getTraceMessage();
  }

  public void setTraceMessage(String expr) throws RemoteException {
    myBreakpoint.setTraceMessage(expr);
  }

  public boolean isSuspend() {
    return myBreakpoint.isSuspend();
  }

  public void setSuspend(boolean suspend) {
    myBreakpoint.setSuspend(suspend);
  }

  public static List<RemoteBreakpoint> convert(List<Breakpoint> list) throws RemoteException {
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
