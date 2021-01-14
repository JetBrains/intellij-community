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

import org.intellij.plugins.xsltDebugger.rt.engine.*;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.net.SocketException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.ArrayList;
import java.util.List;

public class RemoteDebuggerClient implements Debugger {
  private final RemoteDebugger myRemote;
  private final String myAccessToken;
  private final BreakpointManager myBreakpointManager;
  private final OutputEventQueue myEventQueue;

  public RemoteDebuggerClient(int port, String accessToken) throws IOException, NotBoundException {
    Registry registry = LocateRegistry.getRegistry(port);
    myRemote = (RemoteDebugger)registry.lookup("XsltDebugger");
    myAccessToken = accessToken;

    final RemoteBreakpointManager manager = myRemote.getBreakpointManager();
    myBreakpointManager = new MyBreakpointManager(manager);

    final RemoteDebugger.EventQueue eventQueue = myRemote.getEventQueue();
    myEventQueue = new MyOutputEventQueue(eventQueue);
  }

  @Override
  public boolean ping() {
    try {
      return myRemote.ping();
    } catch (RemoteException e) {
      return false;
    }
  }

  @Override
  public State getState() {
    try {
      return myRemote.getState();
    } catch (RemoteException e) {
      throw handleRemoteException(e);
    }
  }

  @Override
  public void stop(boolean force) {
    try {
      myRemote.stop(force);
    } catch (RemoteException e) {
      throw handleRemoteException(e);
    }
  }

  static RuntimeException handleRemoteException(RemoteException e) {
    Throwable t = e.getCause();
    while (t != null) {
      if (t instanceof SocketException || t instanceof EOFException) {
        throw new DebuggerStoppedException();
      }
      t = t.getCause();
    }
    return e.getCause() instanceof RuntimeException ? (RuntimeException)e.getCause() : new RuntimeException(e);
  }

  @Override
  public Debugger.State waitForStateChange(State state) {
    try {
      return myRemote.waitForStateChange(state);
    } catch (RemoteException e) {
      throw handleRemoteException(e);
    }
  }

  @Override
  public boolean waitForDebuggee() {
    try {
      return myRemote.waitForDebuggee();
    } catch (RemoteException e) {
      throw handleRemoteException(e);
    }
  }

  @Override
  public boolean start() {
    try {
      return myRemote.start();
    } catch (RemoteException e) {
      throw handleRemoteException(e);
    }
  }

  @Override
  public void step() {
    try {
      myRemote.step();
    } catch (RemoteException e) {
      throw handleRemoteException(e);
    }
  }

  @Override
  public void stepInto() {
    try {
      myRemote.stepInto();
    } catch (RemoteException e) {
      throw handleRemoteException(e);
    }
  }

  @Override
  public void resume() {
    try {
      myRemote.resume();
    } catch (RemoteException e) {
      throw handleRemoteException(e);
    }
  }

  @Override
  public void pause() {
    try {
      myRemote.pause();
    } catch (RemoteException e) {
      throw handleRemoteException(e);
    }
  }

  @Override
  public boolean isStopped() {
    try {
      return myRemote.isStopped();
    } catch (RemoteException e) {
      throw handleRemoteException(e);
    }
  }

  @Override
  public StyleFrame getCurrentFrame() {
    try {
      return new MyFrame(myRemote.getCurrentFrame());
    } catch (RemoteException e) {
      throw handleRemoteException(e);
    }
  }

  @Override
  public SourceFrame getSourceFrame() {
    try {
      return new MySourceFrame(myRemote.getSourceFrame());
    } catch (RemoteException e) {
      throw handleRemoteException(e);
    }
  }

  @Override
  public Value eval(String expr) throws EvaluationException {
    try {
      return myRemote.eval(expr, myAccessToken);
    } catch (RemoteException e) {
      throw handleRemoteException(e);
    }
  }

  @Override
  public List<Variable> getGlobalVariables() {
    try {
      return MyVariable.convert(myRemote.getGlobalVariables());
    } catch (RemoteException e) {
      throw handleRemoteException(e);
    }
  }

  @Override
  public BreakpointManager getBreakpointManager() {
    return myBreakpointManager;
  }

  @Override
  public OutputEventQueue getEventQueue() {
    return myEventQueue;
  }

  private static class MyBreakpointManager implements BreakpointManager {
    private final RemoteBreakpointManager myManager;

    MyBreakpointManager(RemoteBreakpointManager manager) {
      myManager = manager;
    }

    @Override
    public Breakpoint setBreakpoint(File file, int line) {
      try {
        return new MyBreakpoint(myManager.setBreakpoint(file, line));
      } catch (RemoteException e) {
        throw handleRemoteException(e);
      }
    }

    @Override
    public Breakpoint setBreakpoint(String uri, int line) {
      try {
        return new MyBreakpoint(myManager.setBreakpoint(uri, line));
      } catch (RemoteException e) {
        throw handleRemoteException(e);
      }
    }

    @Override
    public void removeBreakpoint(Breakpoint bp) {
      try {
        myManager.removeBreakpoint(bp.getUri(), bp.getLine());
      } catch (RemoteException e) {
        throw handleRemoteException(e);
      }
    }

    @Override
    public void removeBreakpoint(String uri, int line) {
      try {
        myManager.removeBreakpoint(uri, line);
      } catch (RemoteException e) {
        throw handleRemoteException(e);
      }
    }

    @Override
    public List<Breakpoint> getBreakpoints() {
      try {
        final List<RemoteBreakpoint> list = myManager.getBreakpoints();
        final ArrayList<Breakpoint> breakpoints = new ArrayList<Breakpoint>(list.size());
        for (RemoteBreakpoint breakpoint : list) {
          breakpoints.add(new MyBreakpoint(breakpoint));
        }
        return breakpoints;
      } catch (RemoteException e) {
        throw handleRemoteException(e);
      }
    }

    @Override
    public Breakpoint getBreakpoint(String uri, int lineNumber) {
      try {
        final RemoteBreakpoint breakpoint = myManager.getBreakpoint(uri, lineNumber);
        return breakpoint != null ? new MyBreakpoint(breakpoint) : null;
      } catch (RemoteException e) {
        throw handleRemoteException(e);
      }
    }

    private static class MyBreakpoint implements Breakpoint {
      private final RemoteBreakpoint myBreakpoint;

      MyBreakpoint(RemoteBreakpoint breakpoint) {
        myBreakpoint = breakpoint;
      }

      @Override
      public String getUri() {
        try {
          return myBreakpoint.getUri();
        } catch (RemoteException e) {
          throw handleRemoteException(e);
        }
      }

      @Override
      public int getLine() {
        try {
          return myBreakpoint.getLine();
        } catch (RemoteException e) {
          throw handleRemoteException(e);
        }
      }

      @Override
      public boolean isEnabled() {
        try {
          return myBreakpoint.isEnabled();
        } catch (RemoteException e) {
          throw handleRemoteException(e);
        }
      }

      @Override
      public String getCondition() {
        try {
          return myBreakpoint.getCondition();
        } catch (RemoteException e) {
          throw handleRemoteException(e);
        }
      }

      @Override
      public String getLogMessage() {
        try {
          return myBreakpoint.getLogMessage();
        } catch (RemoteException e) {
          throw handleRemoteException(e);
        }
      }

      @Override
      public void setCondition(String expr) {
        try {
          myBreakpoint.setCondition(expr);
        } catch (RemoteException e) {
          throw handleRemoteException(e);
        }
      }

      @Override
      public void setEnabled(boolean enabled) {
        try {
          myBreakpoint.setEnabled(enabled);
        } catch (RemoteException e) {
          throw handleRemoteException(e);
        }
      }

      @Override
      public void setLogMessage(String expr) {
        try {
          myBreakpoint.setLogMessage(expr);
        } catch (RemoteException e) {
          throw handleRemoteException(e);
        }
      }

      @Override
      public String getTraceMessage() {
        try {
          return myBreakpoint.getTraceMessage();
        } catch (RemoteException e) {
          throw handleRemoteException(e);
        }
      }

      @Override
      public void setTraceMessage(String expr) {
        try {
          myBreakpoint.setTraceMessage(expr);
        } catch (RemoteException e) {
          throw handleRemoteException(e);
        }
      }

      @Override
      public boolean isSuspend() {
        try {
          return myBreakpoint.isSuspend();
        } catch (RemoteException e) {
          throw handleRemoteException(e);
        }
      }

      @Override
      public void setSuspend(boolean suspend) {
        try {
          myBreakpoint.setSuspend(suspend);
        } catch (RemoteException e) {
          throw handleRemoteException(e);
        }
      }
    }
  }

  private abstract class MyAbstractFrame<F extends Frame<?>> implements Frame<F> {
    protected final RemoteDebugger.Frame myFrame;

    protected MyAbstractFrame(RemoteDebugger.Frame frame) {
      myFrame = frame;
    }

    @Override
    public int getLineNumber() {
      try {
        return myFrame.getLineNumber();
      } catch (RemoteException e) {
        throw handleRemoteException(e);
      }
    }

    @Override
    public String getURI() {
      try {
        return myFrame.getURI();
      } catch (RemoteException e) {
        throw handleRemoteException(e);
      }
    }

    @Override
    public F getNext() {
      try {
        return createImpl(myFrame.getNext());
      } catch (RemoteException e) {
        throw handleRemoteException(e);
      }
    }

    @Override
    public F getPrevious() {
      try {
        return createImpl(myFrame.getPrevious());
      } catch (RemoteException e) {
        throw handleRemoteException(e);
      }
    }

    protected abstract F createImpl(RemoteDebugger.Frame frame);

    public String getXPath() {
      try {
        return myFrame.getXPath();
      } catch (RemoteException e) {
        throw handleRemoteException(e);
      }
    }

    public Value eval(String expr) throws EvaluationException {
      try {
        return myFrame.eval(expr, myAccessToken);
      } catch (RemoteException e) {
        throw handleRemoteException(e);
      }
    }

    public List<Variable> getVariables() {
      try {
        return MyVariable.convert(myFrame.getVariables());
      } catch (RemoteException e) {
        throw handleRemoteException(e);
      }
    }
  }

  private class MyFrame extends MyAbstractFrame<StyleFrame> implements StyleFrame {
    protected MyFrame(RemoteDebugger.Frame frame) {
      super(frame);
    }

    @Override
    public String getInstruction() {
      try {
        return myFrame.getInstruction();
      } catch (RemoteException e) {
        throw handleRemoteException(e);
      }
    }

    @Override
    protected StyleFrame createImpl(RemoteDebugger.Frame frame) {
      return create(frame);
    }

    public StyleFrame create(RemoteDebugger.Frame currentFrame) {
      return currentFrame != null ? new MyFrame(currentFrame) : null;
    }
  }

  private class MySourceFrame extends MyAbstractFrame<SourceFrame> implements SourceFrame {
    protected MySourceFrame(RemoteDebugger.Frame frame) {
      super(frame);
    }

    @Override
    public SourceFrame createImpl(RemoteDebugger.Frame frame) {
      return create(frame);
    }

    public SourceFrame create(RemoteDebugger.Frame currentFrame) {
      return currentFrame != null ? new MySourceFrame(currentFrame) : null;
    }
  }

  private static class MyVariable implements Variable {
    private final RemoteDebugger.Variable myVariable;

    MyVariable(RemoteDebugger.Variable variable) {
      myVariable = variable;
    }

    @Override
    public String getURI() {
      try {
        return myVariable.getURI();
      } catch (RemoteException e) {
        throw handleRemoteException(e);
      }
    }

    @Override
    public int getLineNumber() {
      try {
        return myVariable.getLineNumber();
      } catch (RemoteException e) {
        throw handleRemoteException(e);
      }
    }

    @Override
    public boolean isGlobal() {
      try {
        return myVariable.isGlobal();
      } catch (RemoteException e) {
        throw handleRemoteException(e);
      }
    }

    @Override
    public Kind getKind() {
      try {
        return myVariable.getKind();
      } catch (RemoteException e) {
        throw handleRemoteException(e);
      }
    }

    @Override
    public String getName() {
      try {
        return myVariable.getName();
      } catch (RemoteException e) {
        throw handleRemoteException(e);
      }
    }

    @Override
    public Value getValue() {
      try {
        return myVariable.getValue();
      } catch (RemoteException e) {
        throw handleRemoteException(e);
      }
    }

    static List<Variable> convert(List<? extends RemoteDebugger.Variable> list) {
      final ArrayList<Variable> variables = new ArrayList<Variable>(list.size());
      for (final RemoteDebugger.Variable variable : list) {
        variables.add(new MyVariable(variable));
      }
      return variables;
    }
  }

  private static class MyOutputEventQueue implements OutputEventQueue {
    private final RemoteDebugger.EventQueue myEventQueue;

    MyOutputEventQueue(RemoteDebugger.EventQueue eventQueue) {
      myEventQueue = eventQueue;
    }

    @Override
    public void setEnabled(boolean b) {
      try {
        myEventQueue.setEnabled(b);
      } catch (RemoteException e) {
        throw handleRemoteException(e);
      }
    }

    @Override
    public List<NodeEvent> getEvents() {
      try {
        return myEventQueue.getEvents();
      } catch (RemoteException e) {
        throw handleRemoteException(e);
      }
    }
  }
}
