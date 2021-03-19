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

import org.intellij.plugins.xsltDebugger.rt.engine.BreakpointManager;

import java.io.File;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.List;

public final class RemoteBreakpointManagerImpl extends UnicastRemoteObject implements RemoteBreakpointManager {
  private final BreakpointManager myManager;

  public RemoteBreakpointManagerImpl(BreakpointManager manager) throws RemoteException {
    super();
    myManager = manager;
  }

  @Override
  public RemoteBreakpoint setBreakpoint(File file, int line) throws RemoteException {
    return RemoteBreakpointImpl.create(myManager.setBreakpoint(file, line));
  }

  @Override
  public RemoteBreakpoint setBreakpoint(String uri, int line) throws RemoteException {
    return RemoteBreakpointImpl.create(myManager.setBreakpoint(uri, line));
  }

  @Override
  public void removeBreakpoint(String uri, int line) {
    myManager.removeBreakpoint(uri, line);
  }

  @Override
  public List<RemoteBreakpoint> getBreakpoints() throws RemoteException {
    return RemoteBreakpointImpl.convert(myManager.getBreakpoints());
  }

  @Override
  public RemoteBreakpoint getBreakpoint(String uri, int lineNumber) throws RemoteException {
    return RemoteBreakpointImpl.create(myManager.getBreakpoint(uri, lineNumber));
  }
}
