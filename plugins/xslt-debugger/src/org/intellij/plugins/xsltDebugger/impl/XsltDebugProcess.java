// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import org.intellij.plugins.xsltDebugger.XsltDebuggerBundle;
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

  @Override
  public XBreakpointHandler<?> @NotNull [] getBreakpointHandlers() {
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

  public static @Nullable XsltDebugProcess getInstance(ProcessHandler handler) {
    return handler.getUserData(KEY);
  }

  @Override
  public @NotNull ExecutionConsole createConsole() {
    return myExecutionConsole;
  }

  @Override
  protected ProcessHandler doGetProcessHandler() {
    return myProcessHandler;
  }

  @Override
  public @NotNull XDebuggerEditorsProvider getEditorsProvider() {
    return myEditorsProvider;
  }

  @Override
  public void startStepOver(@Nullable XSuspendContext context) {
    myDebuggerSession.stepOver();
  }

  @Override
  public void startStepInto(@Nullable XSuspendContext context) {
    myDebuggerSession.stepInto();
  }

  @Override
  public void startStepOut(@Nullable XSuspendContext context) {
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
      getSession().reportMessage(XsltDebuggerBundle.message("dialog.message.target.vm.not.responding"), MessageType.WARNING);
      return false;
    }
  }

  @Override
  public void resume(@Nullable XSuspendContext context) {
    myDebuggerSession.resume();
  }

  @Override
  public void dispose() {
  }

  @Override
  public void runToPosition(@NotNull XSourcePosition position, @Nullable XSuspendContext context) {
    final PsiFile psiFile = PsiManager.getInstance(getSession().getProject()).findFile(position.getFile());
    assert psiFile != null;
    if (myDebuggerSession.canRunTo(position)) {
      myDebuggerSession.runTo(psiFile, position);
    } else {
      StatusBar.Info.set(XsltDebuggerBundle.message("status.bar.text.not.valid.position.in.file", psiFile.getName()), psiFile.getProject());
      final Debugger c = myDebuggerSession.getClient();
      getSession().positionReached(new MySuspendContext(myDebuggerSession, c.getCurrentFrame(), c.getSourceFrame()));
    }
  }

  private static class MySuspendContext extends XSuspendContext {
    private final XsltDebuggerSession myDebuggerSession;
    private final Debugger.StyleFrame myStyleFrame;
    private final Debugger.SourceFrame mySourceFrame;

    MySuspendContext(XsltDebuggerSession debuggerSession, Debugger.StyleFrame styleFrame, Debugger.SourceFrame sourceFrame) {
      myDebuggerSession = debuggerSession;
      myStyleFrame = styleFrame;
      mySourceFrame = sourceFrame;
    }

    @Override
    public XExecutionStack getActiveExecutionStack() {
      return new XsltExecutionStack(XsltDebuggerBundle.message("list.item.xslt.frames"), myStyleFrame, myDebuggerSession);
    }

    public XExecutionStack getSourceStack() {
      return new XsltExecutionStack(XsltDebuggerBundle.message("list.item.source.frames"), mySourceFrame, myDebuggerSession);
    }

    @Override
    public XExecutionStack @NotNull [] getExecutionStacks() {
      return new XExecutionStack[]{
        getActiveExecutionStack(),
        getSourceStack()
      };
    }
  }
}
