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

import com.icl.saxon.TransformerFactoryImpl;
import org.intellij.plugins.xsltDebugger.rt.engine.Debugger;
import org.intellij.plugins.xsltDebugger.rt.engine.Value;
import org.intellij.plugins.xsltDebugger.rt.engine.local.LocalDebugger;

import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.File;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.List;

public final class DebuggerServer extends UnicastRemoteObject implements RemoteDebugger {
  private static final String XSLT_DEBUGGER = "XsltDebugger";
  private static final int PORT = 34275;

  private final Debugger myDebugger;
  private final RemoteBreakpointManagerImpl myBreakpointManager;
  private final RemoteEventQueueImpl myEventQueue;
  private final int myPort;
  private final String myAccessToken;

  private DebuggerServer(Transformer transformer, Source xml, Result out, int port)
    throws RemoteException {
    myPort = port;
    myAccessToken = System.getProperty("xslt.debugger.token");
    myDebugger = new LocalDebugger(transformer, xml, out) {
      @Override
      public void stop(boolean b) {
        try {
          super.stop(b);
        } finally {
          if (b) System.exit(0);
        }
      }
    };
    myBreakpointManager = new RemoteBreakpointManagerImpl(myDebugger.getBreakpointManager());
    myEventQueue = new RemoteEventQueueImpl(myDebugger.getEventQueue());
  }

  public static DebuggerServer create(Transformer xsl, Source xml, Result out, int port)
    throws RemoteException {
    final DebuggerServer server = new DebuggerServer(xsl, xml, out, port);
    final Registry registry = LocateRegistry.createRegistry(port);
    registry.rebind(XSLT_DEBUGGER, server);
    return server;
  }

  public static DebuggerServer create(File f, File x) throws TransformerConfigurationException, RemoteException {
    return create(new TransformerFactoryImpl().newTransformer(new StreamSource(f)), new StreamSource(x), new StreamResult(), PORT);
  }

  @Override
  public void stop(boolean force) throws RemoteException {
    myDebugger.stop(force);
    try {
      LocateRegistry.getRegistry(myPort).unbind(XSLT_DEBUGGER);
    } catch (NotBoundException e) {
      // hu?
    }
  }

  @Override
  public boolean ping() {
    return myDebugger.ping();
  }

  @Override
  public Debugger.State waitForStateChange(Debugger.State state) {
    return myDebugger.waitForStateChange(state);
  }

  @Override
  public boolean waitForDebuggee() {
    return myDebugger.waitForDebuggee();
  }

  @Override
  public boolean start() {
    return myDebugger.start();
  }

  @Override
  public void step() {
    myDebugger.step();
  }

  @Override
  public void stepInto() {
    myDebugger.stepInto();
  }

  @Override
  public void resume() {
    myDebugger.resume();
  }

  @Override
  public boolean isStopped() {
    return myDebugger.isStopped();
  }

  @Override
  public Frame getCurrentFrame() throws RemoteException {
    return RemoteFrameImpl.create(myDebugger.getCurrentFrame(), myAccessToken);
  }

  @Override
  public Frame getSourceFrame() throws RemoteException {
    return RemoteFrameImpl.create(myDebugger.getSourceFrame(), myAccessToken);
  }

  @Override
  public Value eval(String expr, String accessToken) throws RemoteException, Debugger.EvaluationException {
    return getCurrentFrame().eval(expr, accessToken);
  }

  @Override
  public List<Variable> getGlobalVariables() throws RemoteException {
    return RemoteVariableImpl.convert(myDebugger.getGlobalVariables());
  }

  @Override
  public RemoteBreakpointManager getBreakpointManager() {
    return myBreakpointManager;
  }

  @Override
  public Debugger.State getState() {
    return myDebugger.getState();
  }

  @Override
  public void pause() {
    myDebugger.pause();
  }

  @Override
  public EventQueue getEventQueue() {
    return myEventQueue;
  }
}
