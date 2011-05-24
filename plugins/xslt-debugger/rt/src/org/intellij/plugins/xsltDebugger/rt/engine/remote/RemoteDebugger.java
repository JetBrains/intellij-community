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

import org.intellij.plugins.xsltDebugger.rt.engine.Debugger;
import org.intellij.plugins.xsltDebugger.rt.engine.OutputEventQueue;
import org.intellij.plugins.xsltDebugger.rt.engine.Value;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: sweinreuter
 * Date: 24.05.2007
 */
public interface RemoteDebugger extends Remote {
  boolean ping() throws RemoteException;

  void stop(boolean force) throws RemoteException;

  Debugger.State waitForStateChange(Debugger.State currentState) throws RemoteException;

  boolean waitForDebuggee() throws RemoteException;

  boolean start() throws RemoteException;

  void step() throws RemoteException;

  void stepInto() throws RemoteException;

  void resume() throws RemoteException;

  boolean isStopped() throws RemoteException;

  Frame getCurrentFrame() throws RemoteException;

  Frame getSourceFrame() throws RemoteException;

  Value eval(String expr) throws RemoteException, Debugger.EvaluationException;

  List<Variable> getGlobalVariables() throws RemoteException;

  RemoteBreakpointManager getBreakpointManager() throws RemoteException;

  Debugger.State getState() throws RemoteException;

  void pause() throws RemoteException;

  EventQueue getEventQueue() throws RemoteException;

  interface EventQueue extends Remote {
    List<OutputEventQueue.NodeEvent> getEvents() throws RemoteException;

    void setEnabled(boolean b) throws RemoteException;
  }

  interface Frame extends Remote {
    int getLineNumber() throws RemoteException;

    String getURI() throws RemoteException;

    Frame getNext() throws RemoteException;

    Frame getPrevious() throws RemoteException;

    String getXPath() throws RemoteException;

    Value eval(String expr) throws RemoteException, Debugger.EvaluationException;

    List<Variable> getVariables() throws RemoteException;

    String getInstruction() throws RemoteException;
  }

  public interface Variable extends Remote {
    boolean isGlobal() throws RemoteException;

    Debugger.Variable.Kind getKind() throws RemoteException;

    String getName() throws RemoteException;

    Value getValue() throws RemoteException;

    String getURI() throws RemoteException;

    int getLineNumber() throws RemoteException;
  }
}
