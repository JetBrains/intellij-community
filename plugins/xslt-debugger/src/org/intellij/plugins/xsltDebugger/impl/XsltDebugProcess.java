package org.intellij.plugins.xsltDebugger.impl;

import com.intellij.execution.ExecutionResult;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.ExecutionConsole;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.xdebugger.XDebugProcess;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.breakpoints.XBreakpointHandler;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import com.intellij.xdebugger.frame.XExecutionStack;
import com.intellij.xdebugger.frame.XSuspendContext;
import org.intellij.lang.xpath.xslt.impl.XsltChecker;
import org.intellij.plugins.xsltDebugger.VMPausedException;
import org.intellij.plugins.xsltDebugger.XsltBreakpointType;
import org.intellij.plugins.xsltDebugger.XsltDebuggerSession;
import org.intellij.plugins.xsltDebugger.rt.engine.Breakpoint;
import org.intellij.plugins.xsltDebugger.rt.engine.BreakpointManager;
import org.intellij.plugins.xsltDebugger.rt.engine.BreakpointManagerImpl;
import org.intellij.plugins.xsltDebugger.rt.engine.Debugger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class XsltDebugProcess extends XDebugProcess implements Disposable {
  private static final Key<XsltDebugProcess> KEY = Key.create("PROCESS");

  private final XsltDebuggerEditorsProvider myEditorsProvider;
  private final ProcessHandler myProcessHandler;
  private final ExecutionConsole myExecutionConsole;

  private BreakpointManager myBreakpointManager = new BreakpointManagerImpl();

  private final XBreakpointHandler<?>[] myXBreakpointHandlers = new XBreakpointHandler<?>[]{
    new XsltBreakpointHandler(this, XsltBreakpointType.class),
  };
  private XsltDebuggerSession myDebuggerSession;

  public XsltDebugProcess(XDebugSession session, ExecutionResult executionResult, XsltChecker.LanguageLevel data) {
    super(session);
    myProcessHandler = executionResult.getProcessHandler();
    myProcessHandler.putUserData(KEY, this);
    myExecutionConsole = executionResult.getExecutionConsole();
    myEditorsProvider = new XsltDebuggerEditorsProvider(data);
    Disposer.register(myExecutionConsole, this);
  }

  @NotNull
  @Override
  public XBreakpointHandler<?>[] getBreakpointHandlers() {
    return myXBreakpointHandlers;
  }

  public BreakpointManager getBreakpointManager() {
    return myBreakpointManager;
  }

  public void init(Debugger client) {
    myDebuggerSession = XsltDebuggerSession.getInstance(myProcessHandler);

    myDebuggerSession.addListener(new XsltDebuggerSession.Listener() {
      @Override
      public void debuggerSuspended() {
        final Debugger c = myDebuggerSession.getClient();
        getSession().positionReached(new MySuspendContext(myDebuggerSession, c.getCurrentFrame(), c.getSourceFrame()));
      }

      @Override
      public void debuggerResumed() {
      }

      @Override
      public void debuggerStopped() {
        myBreakpointManager = new BreakpointManagerImpl();
      }
    });

    final BreakpointManager mgr = client.getBreakpointManager();
    if (myBreakpointManager != mgr) {
      final List<Breakpoint> breakpoints = myBreakpointManager.getBreakpoints();
      for (Breakpoint breakpoint : breakpoints) {
        final Breakpoint bp = mgr.setBreakpoint(breakpoint.getUri(), breakpoint.getLine());
        bp.setEnabled(breakpoint.isEnabled());
        bp.setLogMessage(breakpoint.getLogMessage());
        bp.setTraceMessage(breakpoint.getTraceMessage());
        bp.setCondition(breakpoint.getCondition());
        bp.setSuspend(breakpoint.isSuspend());
      }
      myBreakpointManager = mgr;
    }
  }

  @Nullable
  public static XsltDebugProcess getInstance(ProcessHandler handler) {
    return handler.getUserData(KEY);
  }

  @NotNull
  @Override
  public ExecutionConsole createConsole() {
    return myExecutionConsole;
  }

  @Override
  protected ProcessHandler doGetProcessHandler() {
    return myProcessHandler;
  }

  @NotNull
  @Override
  public XDebuggerEditorsProvider getEditorsProvider() {
    return myEditorsProvider;
  }

  @Override
  public void startStepOver() {
    myDebuggerSession.stepOver();
  }

  @Override
  public void startStepInto() {
    myDebuggerSession.stepInto();
  }

  @Override
  public void startStepOut() {
    myDebuggerSession.stepOver();
  }

  @Override
  public void stop() {
    if (myDebuggerSession != null) {
      myDebuggerSession.stop();
    }
  }

  @Override
  public boolean checkCanPerformCommands() {
    if (myDebuggerSession == null) return super.checkCanPerformCommands();

    try {
      return myDebuggerSession.getClient().ping();
    } catch (VMPausedException e) {
      getSession().reportMessage(VMPausedException.MESSAGE, MessageType.WARNING);
      return false;
    }
  }

  @Override
  public void resume() {
    myDebuggerSession.resume();
  }

  @Override
  public void dispose() {
  }

  @Override
  public void runToPosition(@NotNull XSourcePosition position) {
    final PsiFile psiFile = PsiManager.getInstance(getSession().getProject()).findFile(position.getFile());
    assert psiFile != null;
    if (myDebuggerSession.canRunTo(position)) {
      myDebuggerSession.runTo(psiFile, position);
    } else {
      StatusBar.Info.set("Not a valid position in file '" + psiFile.getName() + "'", psiFile.getProject());
      final Debugger c = myDebuggerSession.getClient();
      getSession().positionReached(new MySuspendContext(myDebuggerSession, c.getCurrentFrame(), c.getSourceFrame()));
    }
  }

  private static class MySuspendContext extends XSuspendContext {
    private final XsltDebuggerSession myDebuggerSession;
    private final Debugger.StyleFrame myStyleFrame;
    private final Debugger.SourceFrame mySourceFrame;

    public MySuspendContext(XsltDebuggerSession debuggerSession, Debugger.StyleFrame styleFrame, Debugger.SourceFrame sourceFrame) {
      myDebuggerSession = debuggerSession;
      myStyleFrame = styleFrame;
      mySourceFrame = sourceFrame;
    }

    @Override
    public XExecutionStack getActiveExecutionStack() {
      return new XsltExecutionStack("XSLT Frames", myStyleFrame, myDebuggerSession);
    }

    public XExecutionStack getSourceStack() {
      return new XsltExecutionStack("Source Frames", mySourceFrame, myDebuggerSession);
    }

    @Override
    public XExecutionStack[] getExecutionStacks() {
      return new XExecutionStack[]{
        getActiveExecutionStack(),
        getSourceStack()
      };
    }
  }
}
