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
package org.intellij.plugins.xsltDebugger.rt.engine.local;

import org.intellij.plugins.xsltDebugger.rt.engine.*;
import org.intellij.plugins.xsltDebugger.rt.engine.local.saxon.SaxonSupport;
import org.intellij.plugins.xsltDebugger.rt.engine.local.saxon9.Saxon9Support;
import org.intellij.plugins.xsltDebugger.rt.engine.local.xalan.XalanSupport;

import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: sweinreuter
 * Date: 19.05.2007
 */
public class LocalDebugger implements Debugger {
  private final Thread myThread;

  private final BreakpointManager myBreakpointManager;
  private final OutputEventQueueImpl myEventQueue;
  private volatile Condition myCurrentStopCondition;

  private final Object theLock = new Object();

  private volatile State myState = State.CREATED;

  private final LinkedList<StyleFrame> myFrames = new LinkedList<StyleFrame>();
  private final LinkedList<SourceFrame> mySourceFrames = new LinkedList<SourceFrame>();

  public LocalDebugger(final Transformer transformer, final Source source, final Result result) {
    prepareTransformer(transformer);

    myBreakpointManager = new BreakpointManagerImpl();
    myEventQueue = new OutputEventQueueImpl(this);

    myThread = new Thread(new Runnable() {
      public void run() {
        try {
          synchronized (theLock) {
            myState = State.RUNNING;
            theLock.notifyAll();
          }
          transformer.transform(source, result);
          stopped();
        } catch (DebuggerStoppedException e) {
          // OK
        } catch (TransformerException e) {
          // TODO: log, pass to client
          e.printStackTrace();
          stopped();
        }
      }
    },"local debugger");
  }

  protected void prepareTransformer(Transformer transformer) {
    try {
      if (Saxon9Support.init(transformer, this)) {
        return;
      }
    } catch (NoClassDefFoundError e1) {
      // ignore
    }
    try {
      if (SaxonSupport.init(transformer, this)) {
        return;
      }
    } catch (NoClassDefFoundError e) {
      // ignore
    }
    try {
      if (XalanSupport.init(transformer, this)) {
        return;
      }
    } catch (NoClassDefFoundError e1) {
      // ignore
    }
    throw new UnsupportedOperationException("Unsupported Transformer: " + transformer.getClass().getName());
  }

  private void suspendAndWait() throws DebuggerStoppedException {
    try {
      synchronized (theLock) {
        myCurrentStopCondition = null;

        myState = State.SUSPENDED;
        theLock.notifyAll();

        do {
          theLock.wait();
        }
        while (myState == State.SUSPENDED);

        if (myState == State.STOPPED) {
          throw new DebuggerStoppedException();
        }
      }
    } catch (InterruptedException e) {
      throw new DebuggerStoppedException();
    }
  }

  public void resume() throws DebuggerStoppedException {
    synchronized (theLock) {
      if (myState == State.STOPPED) {
        throw new DebuggerStoppedException();
      } else if (myState != State.SUSPENDED) {
        throw new IllegalStateException();
      }

      myState = State.RUNNING;
      theLock.notifyAll();
    }
  }

  public void pause() {
    synchronized (theLock) {
      if (myState == State.STOPPED) {
        throw new DebuggerStoppedException();
      } else if (myState != State.RUNNING) {
        throw new IllegalStateException();
      }

      myCurrentStopCondition = Condition.TRUE;
    }
  }

  public void stopped() {
    assert Thread.currentThread() == myThread;
    stop0();
  }

  private void stop0() {
    synchronized (theLock) {
      myState = State.STOPPED;
      theLock.notifyAll();
    }
  }

  public State getState() {
    synchronized (theLock) {
      return myState;
    }
  }

  @SuppressWarnings({ "deprecation" })
  public void stop(boolean force) {
    stop0();
    myThread.interrupt();

    if (!force) {
      return;
    }
    try {
      myThread.join(1000);
      if (myThread.isAlive()) {
        myThread.stop();
      }
    } catch (InterruptedException e) {
      //
    }
  }

  public State waitForStateChange(State state) {
    try {
      synchronized (theLock) {
        if (myState == State.STOPPED) {
          return State.STOPPED;
        }
        while (myState == state) {
          theLock.wait();
        }

        return myState;
      }
    } catch (InterruptedException e) {
      return null;
    }
  }

  public boolean waitForDebuggee() {
    try {
      synchronized (theLock) {
        while (myState == State.RUNNING) {
          theLock.wait();
        }

        return myState != State.STOPPED;
      }
    } catch (InterruptedException e) {
      return false;
    }
  }

  public boolean isStopped() {
    synchronized (theLock) {
      return myState == State.STOPPED;
    }
  }

  public boolean start() {
    assert myState == State.CREATED : "Already started";

    myThread.start();

    try {
      synchronized (theLock) {
        while (myState == State.CREATED) {
          theLock.wait();
        }
      }
      return true;
    } catch (InterruptedException e) {
      //
    }
    return false;
  }

  public void enter(StyleFrame frame) {
    assert Thread.currentThread() == myThread;

    myFrames.addFirst(frame);
    final String uri = frame.getURI();

    final StyleFrame previous = frame.getPrevious();
    //if (previous != null && previous.getLineNumber() == frame.getLineNumber() && uri != null && uri.equals(previous.getURI())) {
    //  if (frame.getInstruction().equals(previous.getInstruction())) {
    //    System.err.println(
    //      "WARN: Same instruction <" + frame.getInstruction() + "> on line " + frame.getLineNumber() + " encountered more than once");
    //  }
    //}

    if (isStopped()) {
      throw new DebuggerStoppedException();
    } else if (myCurrentStopCondition != null && myCurrentStopCondition.value()) {
      suspendAndWait();
    } else {
      final int lineNumber = frame.getLineNumber();
      final Breakpoint breakpoint = myBreakpointManager.getBreakpoint(uri, lineNumber);
      if (breakpoint != null && breakpoint.isEnabled()) {
        // do not evaluate a log or condition bp more than once on the same line
        if (previous == null || previous.getLineNumber() != lineNumber) {
          final String condition = breakpoint.getCondition();
          try {
            if (evalCondition(condition)) {
              final String logMessage = breakpoint.getLogMessage();
              final String traceMessage = breakpoint.getTraceMessage();

              if (logBreakpoint(frame, logMessage, traceMessage) || breakpoint.isSuspend()) {
                suspendAndWait();
              }
            }
          } catch (EvaluationException e) {
            // TODO: send to IDEA
            System.err.println("[" + lineNumber + "]: Failed to evaluate expression: " + condition + " -- " + e.getMessage());
            breakpoint.setEnabled(false);
          }
        }
      }
    }
  }

  private boolean evalCondition(String condition) throws EvaluationException {
    if (condition != null && condition.length() > 0) {
      if (!"true".equals(eval("boolean(" + condition + ")").getValue().toString())) {
        return false;
      }
    }
    return true;
  }

  private boolean logBreakpoint(StyleFrame frame, String logMessage, String traceMessage) throws EvaluationException {
    if (logMessage != null) {
      final String uri = frame.getURI();
      final String pos = uri.substring(uri.lastIndexOf('/') + 1) + ":" + frame.getLineNumber();
      System.out.println("[" + pos + "]: " + (logMessage.length() > 0 ? eval(logMessage).getValue().toString() : "<no message>"));

      if (traceMessage != null) {
        myEventQueue.trace(makeTraceMessage(traceMessage));
      }
      return false;
    } else if (traceMessage != null) {
      myEventQueue.trace(makeTraceMessage(traceMessage));
      return false;
    }
    return true;
  }

  private String makeTraceMessage(String traceMessage) throws EvaluationException {
    if (traceMessage.length() > 0) {
      return eval(traceMessage).getValue().toString();
    } else {
      return null;
    }
  }

  public void leave() {
    assert Thread.currentThread() == myThread;

    if (isStopped()) {
      throw new DebuggerStoppedException();
//        } else if (myBreakpointManager.isBreakpoint(uri, lineNumber)) {
//            suspendAndWait();
//        } else if (myCurrentStopCondition != null && myCurrentStopCondition.value()) {
//            suspendAndWait();
    }

    ((AbstractFrame)myFrames.removeFirst()).invalidate();
  }

  public void step() {
    final int targetSize = myFrames.size();

    myCurrentStopCondition = new Condition() {
      public boolean value() {
        return myFrames.size() <= targetSize;
      }
    };
    resume();
  }

  public void stepInto() {
    myCurrentStopCondition = Condition.TRUE;

    resume();
  }

  public org.intellij.plugins.xsltDebugger.rt.engine.Value eval(String expr) throws EvaluationException {
    final StyleFrame frame = getCurrentFrame();
    if (frame == null) {
      throw new EvaluationException("No frame available");
    }
    return frame.eval(expr);
  }

  public StyleFrame getCurrentFrame() {
    return myFrames.size() > 0 ? myFrames.getFirst() : null;
  }

  public SourceFrame getSourceFrame() {
    return mySourceFrames.size() > 0 ? mySourceFrames.getFirst() : null;
  }

  public List<Debugger.Variable> getGlobalVariables() {
    final List<Variable> vars = getCurrentFrame().getVariables();
    for (Iterator<Variable> it = vars.iterator(); it.hasNext(); ) {
      Variable var = it.next();
      if (!var.isGlobal()) {
        it.remove();
      }
    }
    return vars;
  }

  public BreakpointManager getBreakpointManager() {
    return myBreakpointManager;
  }

  public void pushSource(SourceFrame sourceFrame) {
    mySourceFrames.addFirst(sourceFrame);
  }

  public void popSource() {
    ((AbstractFrame)mySourceFrames.removeFirst()).invalidate();
  }

  interface Condition {
    Condition TRUE = new Condition() {
      public boolean value() {
        return true;
      }
    };

    boolean value();
  }

  public OutputEventQueueImpl getEventQueue() {
    return myEventQueue;
  }

  public boolean ping() {
    return true;
  }
}
