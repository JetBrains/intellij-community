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
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: sweinreuter
 * Date: 23.05.2007
 */
public class RemoteDebuggerClient implements Debugger {
  private final RemoteDebugger myRemote;
  private final BreakpointManager myBreakpointManager;
  private final OutputEventQueue myEventQueue;

  public RemoteDebuggerClient(int port) throws IOException, NotBoundException {
    myRemote = (RemoteDebugger)Naming.lookup("rmi://127.0.0.1:" + port + "/XsltDebugger");

    final RemoteBreakpointManager manager = myRemote.getBreakpointManager();
    myBreakpointManager = new MyBreakpointManager(manager);

    final RemoteDebugger.EventQueue eventQueue = myRemote.getEventQueue();
    myEventQueue = new MyOutputEventQueue(eventQueue);
  }

  public boolean ping() {
    try {
      return myRemote.ping();
    } catch (RemoteException e) {
      return false;
    }
  }

  public State getState() {
    try {
      return myRemote.getState();
    } catch (RemoteException e) {
      throw handleRemoteException(e);
    }
  }

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

  public Debugger.State waitForStateChange(State state) {
    try {
      return myRemote.waitForStateChange(state);
    } catch (RemoteException e) {
      throw handleRemoteException(e);
    }
  }

  public boolean waitForDebuggee() {
    try {
      return myRemote.waitForDebuggee();
    } catch (RemoteException e) {
      throw handleRemoteException(e);
    }
  }

  public boolean start() {
    try {
      return myRemote.start();
    } catch (RemoteException e) {
      throw handleRemoteException(e);
    }
  }

  public void step() {
    try {
      myRemote.step();
    } catch (RemoteException e) {
      throw handleRemoteException(e);
    }
  }

  public void stepInto() {
    try {
      myRemote.stepInto();
    } catch (RemoteException e) {
      throw handleRemoteException(e);
    }
  }

  public void resume() {
    try {
      myRemote.resume();
    } catch (RemoteException e) {
      throw handleRemoteException(e);
    }
  }

  public void pause() {
    try {
      myRemote.pause();
    } catch (RemoteException e) {
      throw handleRemoteException(e);
    }
  }

  public boolean isStopped() {
    try {
      return myRemote.isStopped();
    } catch (RemoteException e) {
      throw handleRemoteException(e);
    }
  }

  public StyleFrame getCurrentFrame() {
    try {
      return MyFrame.create(myRemote.getCurrentFrame());
    } catch (RemoteException e) {
      throw handleRemoteException(e);
    }
  }

  public SourceFrame getSourceFrame() {
    try {
      return MySourceFrame.create(myRemote.getSourceFrame());
    } catch (RemoteException e) {
      throw handleRemoteException(e);
    }
  }

  public Value eval(String expr) throws EvaluationException {
    try {
      return myRemote.eval(expr);
    } catch (RemoteException e) {
      throw handleRemoteException(e);
    }
  }

  public List<Variable> getGlobalVariables() {
    try {
      return MyVariable.convert(myRemote.getGlobalVariables());
    } catch (RemoteException e) {
      throw handleRemoteException(e);
    }
  }

  public BreakpointManager getBreakpointManager() {
    return myBreakpointManager;
  }

  public OutputEventQueue getEventQueue() {
    return myEventQueue;
  }

  private static class MyBreakpointManager implements BreakpointManager {
    private final RemoteBreakpointManager myManager;

    public MyBreakpointManager(RemoteBreakpointManager manager) {
      myManager = manager;
    }

    public Breakpoint setBreakpoint(File file, int line) {
      try {
        return new MyBreakpoint(myManager.setBreakpoint(file, line));
      } catch (RemoteException e) {
        throw handleRemoteException(e);
      }
    }

    public Breakpoint setBreakpoint(String uri, int line) {
      try {
        return new MyBreakpoint(myManager.setBreakpoint(uri, line));
      } catch (RemoteException e) {
        throw handleRemoteException(e);
      }
    }

    public void removeBreakpoint(Breakpoint bp) {
      try {
        myManager.removeBreakpoint(bp.getUri(), bp.getLine());
      } catch (RemoteException e) {
        throw handleRemoteException(e);
      }
    }

    public void removeBreakpoint(String uri, int line) {
      try {
        myManager.removeBreakpoint(uri, line);
      } catch (RemoteException e) {
        throw handleRemoteException(e);
      }
    }

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

      public MyBreakpoint(RemoteBreakpoint breakpoint) {
        myBreakpoint = breakpoint;
      }

      public String getUri() {
        try {
          return myBreakpoint.getUri();
        } catch (RemoteException e) {
          throw handleRemoteException(e);
        }
      }

      public int getLine() {
        try {
          return myBreakpoint.getLine();
        } catch (RemoteException e) {
          throw handleRemoteException(e);
        }
      }

      public boolean isEnabled() {
        try {
          return myBreakpoint.isEnabled();
        } catch (RemoteException e) {
          throw handleRemoteException(e);
        }
      }

      public String getCondition() {
        try {
          return myBreakpoint.getCondition();
        } catch (RemoteException e) {
          throw handleRemoteException(e);
        }
      }

      public String getLogMessage() {
        try {
          return myBreakpoint.getLogMessage();
        } catch (RemoteException e) {
          throw handleRemoteException(e);
        }
      }

      public void setCondition(String expr) {
        try {
          myBreakpoint.setCondition(expr);
        } catch (RemoteException e) {
          throw handleRemoteException(e);
        }
      }

      public void setEnabled(boolean enabled) {
        try {
          myBreakpoint.setEnabled(enabled);
        } catch (RemoteException e) {
          throw handleRemoteException(e);
        }
      }

      public void setLogMessage(String expr) {
        try {
          myBreakpoint.setLogMessage(expr);
        } catch (RemoteException e) {
          throw handleRemoteException(e);
        }
      }

      public String getTraceMessage() {
        try {
          return myBreakpoint.getTraceMessage();
        } catch (RemoteException e) {
          throw handleRemoteException(e);
        }
      }

      public void setTraceMessage(String expr) {
        try {
          myBreakpoint.setTraceMessage(expr);
        } catch (RemoteException e) {
          throw handleRemoteException(e);
        }
      }

      public boolean isSuspend() {
        try {
          return myBreakpoint.isSuspend();
        } catch (RemoteException e) {
          throw handleRemoteException(e);
        }
      }

      public void setSuspend(boolean suspend) {
        try {
          myBreakpoint.setSuspend(suspend);
        } catch (RemoteException e) {
          throw handleRemoteException(e);
        }
      }
    }
  }

  private static abstract class MyAbstractFrame<F extends Frame> implements Frame<F> {
    protected final RemoteDebugger.Frame myFrame;

    protected MyAbstractFrame(RemoteDebugger.Frame frame) {
      myFrame = frame;
    }

    public int getLineNumber() {
      try {
        return myFrame.getLineNumber();
      } catch (RemoteException e) {
        throw handleRemoteException(e);
      }
    }

    public String getURI() {
      try {
        return myFrame.getURI();
      } catch (RemoteException e) {
        throw handleRemoteException(e);
      }
    }

    public F getNext() {
      try {
        return createImpl(myFrame.getNext());
      } catch (RemoteException e) {
        throw handleRemoteException(e);
      }
    }

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
        return myFrame.eval(expr);
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

  private static class MyFrame extends MyAbstractFrame<StyleFrame> implements StyleFrame {
    protected MyFrame(RemoteDebugger.Frame frame) {
      super(frame);
    }

    public String getInstruction() {
      try {
        return myFrame.getInstruction();
      } catch (RemoteException e) {
        throw handleRemoteException(e);
      }
    }

    protected StyleFrame createImpl(RemoteDebugger.Frame frame) {
      return create(frame);
    }

    public static StyleFrame create(RemoteDebugger.Frame currentFrame) {
      return currentFrame != null ? new MyFrame(currentFrame) : null;
    }
  }

  private static class MySourceFrame extends MyAbstractFrame<SourceFrame> implements SourceFrame {
    protected MySourceFrame(RemoteDebugger.Frame frame) {
      super(frame);
    }

    public SourceFrame createImpl(RemoteDebugger.Frame frame) {
      return create(frame);
    }

    public static SourceFrame create(RemoteDebugger.Frame currentFrame) {
      return currentFrame != null ? new MySourceFrame(currentFrame) : null;
    }
  }

  private static class MyVariable implements Variable {
    private final RemoteDebugger.Variable myVariable;

    public MyVariable(RemoteDebugger.Variable variable) {
      myVariable = variable;
    }

    public String getURI() {
      try {
        return myVariable.getURI();
      } catch (RemoteException e) {
        throw handleRemoteException(e);
      }
    }

    public int getLineNumber() {
      try {
        return myVariable.getLineNumber();
      } catch (RemoteException e) {
        throw handleRemoteException(e);
      }
    }

    public boolean isGlobal() {
      try {
        return myVariable.isGlobal();
      } catch (RemoteException e) {
        throw handleRemoteException(e);
      }
    }

    public Kind getKind() {
      try {
        return myVariable.getKind();
      } catch (RemoteException e) {
        throw handleRemoteException(e);
      }
    }

    public String getName() {
      try {
        return myVariable.getName();
      } catch (RemoteException e) {
        throw handleRemoteException(e);
      }
    }

    public Value getValue() {
      try {
        return myVariable.getValue();
      } catch (RemoteException e) {
        throw handleRemoteException(e);
      }
    }

    static List<Variable> convert(List<RemoteDebugger.Variable> list) {
      final ArrayList<Variable> variables = new ArrayList<Variable>(list.size());
      for (final RemoteDebugger.Variable variable : list) {
        variables.add(new MyVariable(variable));
      }
      return variables;
    }
  }

  private static class MyOutputEventQueue implements OutputEventQueue {
    private final RemoteDebugger.EventQueue myEventQueue;

    public MyOutputEventQueue(RemoteDebugger.EventQueue eventQueue) {
      myEventQueue = eventQueue;
    }

    public void setEnabled(boolean b) {
      try {
        myEventQueue.setEnabled(b);
      } catch (RemoteException e) {
        throw handleRemoteException(e);
      }
    }

    public List<NodeEvent> getEvents() {
      try {
        return myEventQueue.getEvents();
      } catch (RemoteException e) {
        throw handleRemoteException(e);
      }
    }
  }
}
