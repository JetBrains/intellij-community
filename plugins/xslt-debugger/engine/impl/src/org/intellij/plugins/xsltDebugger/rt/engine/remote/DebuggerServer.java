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

import javax.rmi.PortableRemoteObject;
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
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: sweinreuter
 * Date: 23.05.2007
 */
public class DebuggerServer extends PortableRemoteObject implements RemoteDebugger {
  private static final String XSLT_DEBUGGER = "XsltDebugger";
  public static final int PORT = 34275;

  private final Debugger myDebugger;
  private final RemoteBreakpointManagerImpl myBreakpointManager;
  private final RemoteEventQueueImpl myEventQueue;
  private final int myPort;

  private DebuggerServer(Transformer transformer, Source xml, Result out, int port)
    throws TransformerConfigurationException, RemoteException {
    myPort = port;
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
    throws TransformerConfigurationException, RemoteException {
    final DebuggerServer server = new DebuggerServer(xsl, xml, out, port);
    final Registry registry = LocateRegistry.createRegistry(port);
    registry.rebind(XSLT_DEBUGGER, server);
    return server;
  }

  public static DebuggerServer create(File f, File x) throws TransformerConfigurationException, RemoteException {
    return create(new TransformerFactoryImpl().newTransformer(new StreamSource(f)), new StreamSource(x), new StreamResult(), PORT);
  }

  public void stop(boolean force) throws RemoteException {
    myDebugger.stop(force);
    try {
      LocateRegistry.getRegistry(myPort).unbind(XSLT_DEBUGGER);
    } catch (NotBoundException e) {
      // hu?
    }
  }

  public boolean ping() throws RemoteException {
    return myDebugger.ping();
  }

  public Debugger.State waitForStateChange(Debugger.State state) throws RemoteException {
    return myDebugger.waitForStateChange(state);
  }

  public boolean waitForDebuggee() throws RemoteException {
    return myDebugger.waitForDebuggee();
  }

  public boolean start() throws RemoteException {
    return myDebugger.start();
  }

  public void step() throws RemoteException {
    myDebugger.step();
  }

  public void stepInto() throws RemoteException {
    myDebugger.stepInto();
  }

  public void resume() throws RemoteException {
    myDebugger.resume();
  }

  public boolean isStopped() throws RemoteException {
    return myDebugger.isStopped();
  }

  public Frame getCurrentFrame() throws RemoteException {
    return RemoteFrameImpl.create(myDebugger.getCurrentFrame());
  }

  public Frame getSourceFrame() throws RemoteException {
    return RemoteFrameImpl.create(myDebugger.getSourceFrame());
  }

  public Value eval(String expr) throws RemoteException, Debugger.EvaluationException {
    return getCurrentFrame().eval(expr);
  }

  public List<Variable> getGlobalVariables() throws RemoteException {
    return RemoteVariableImpl.convert(myDebugger.getGlobalVariables());
  }

  public RemoteBreakpointManager getBreakpointManager() throws RemoteException {
    return myBreakpointManager;
  }

  public Debugger.State getState() throws RemoteException {
    return myDebugger.getState();
  }

  public void pause() throws RemoteException {
    myDebugger.pause();
  }

  public EventQueue getEventQueue() throws RemoteException {
    return myEventQueue;
  }
}
