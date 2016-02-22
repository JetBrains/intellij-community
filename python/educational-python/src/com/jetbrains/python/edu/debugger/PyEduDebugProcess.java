package com.jetbrains.python.edu.debugger;

import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.ExecutionConsole;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.xdebugger.XDebugSession;
import com.jetbrains.python.PythonHelpersLocator;
import com.jetbrains.python.debugger.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

class PyEduDebugProcess extends PyDebugProcess {

  private final String myScriptName;
  private final int myLine;

  public PyEduDebugProcess(@NotNull XDebugSession session,
                           @NotNull ServerSocket serverSocket,
                           @NotNull ExecutionConsole executionConsole,
                           @Nullable ProcessHandler processHandler, boolean multiProcess,
                           String scriptName,
                           int line) {
    super(session, serverSocket, executionConsole, processHandler, multiProcess);
    myScriptName = scriptName;
    myLine = line;
  }

  @Override
  public PyStackFrame createStackFrame(PyStackFrameInfo frameInfo) {
    return new PyEduStackFrame(getSession().getProject(), this, frameInfo,
                               getPositionConverter().convertFromPython(frameInfo.getPosition()));
  }

  @Override
  public void init() {
    super.init();
    addTemporaryBreakpoint(PyLineBreakpointType.ID, myScriptName, myLine);
  }

  @NotNull
  @Override
  protected PySuspendContext createSuspendContext(PyThreadInfo threadInfo) {
    threadInfo.updateState(threadInfo.getState(), new ArrayList<PyStackFrameInfo>(filterFrames(threadInfo.getFrames())));
    return new PySuspendContext(this, threadInfo);
  }

  public Collection<PyStackFrameInfo> filterFrames(@Nullable List<PyStackFrameInfo> frames) {
    if (frames == null) {
      return Collections.emptyList();
    }
    final String helpersPath = PythonHelpersLocator.getHelpersRoot().getPath();
    return Collections2.filter(frames, new Predicate<PyStackFrameInfo>() {
      @Override
      public boolean apply(PyStackFrameInfo frame) {
        String file = frame.getPosition().getFile();
        return !FileUtil.isAncestor(helpersPath, file, false);
      }
    });
  }
}
