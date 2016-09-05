package org.jetbrains.debugger.memory.tracking;

import com.intellij.debugger.DebuggerManager;
import com.intellij.debugger.engine.DebugProcess;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.requests.ClassPrepareRequestor;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.XDebugSession;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.request.ClassPrepareRequest;
import org.jetbrains.annotations.NotNull;

public abstract class ClassPreparedListener {
  private final XDebugSession myDebugSession;
  private final Project myProject;

  protected ClassPreparedListener(@NotNull String className,
                                  @NotNull XDebugSession debugSession) {
    myProject = debugSession.getProject();
    myDebugSession = debugSession;
    DebugProcessImpl debugProcess = getDebugProcess();
    ClassPrepareRequestor request = new MyClassPreparedRequest();
    ClassPrepareRequest classPrepareRequest = debugProcess.getRequestsManager()
        .createClassPrepareRequest(request, className);
    if (classPrepareRequest != null) {
      classPrepareRequest.enable();
    }
  }

  public abstract void onClassPrepared(@NotNull ReferenceType referenceType);

  private DebugProcessImpl getDebugProcess() {
    return (DebugProcessImpl) DebuggerManager.getInstance(myProject)
        .getDebugProcess(myDebugSession.getDebugProcess().getProcessHandler());
  }

  private final class MyClassPreparedRequest implements ClassPrepareRequestor {
    @Override
    public void processClassPrepare(DebugProcess debuggerProcess, ReferenceType referenceType) {
      getDebugProcess().getRequestsManager().deleteRequest(this);
      onClassPrepared(referenceType);
    }
  }
}
