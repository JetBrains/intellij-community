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

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * Created by IntelliJ IDEA.
 * User: sweinreuter
 * Date: 29.05.2007
 */
public interface RemoteBreakpoint extends Remote {
  String getUri() throws RemoteException;

  int getLine() throws RemoteException;

  boolean isEnabled() throws RemoteException;

  void setEnabled(boolean b) throws RemoteException;

  String getCondition() throws RemoteException;

  void setCondition(String expr) throws RemoteException;

  String getLogMessage() throws RemoteException;

  void setLogMessage(String expr) throws RemoteException;

  String getTraceMessage() throws RemoteException;

  void setTraceMessage(String expr) throws RemoteException;

  boolean isSuspend() throws RemoteException;

  void setSuspend(boolean suspend) throws RemoteException;
}
