package com.intellij.debugger.engine;
import com.intellij.debugger.engine.DebugProcess;
import com.intellij.debugger.DebuggerManager;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.application.ModalityState;

import java.io.OutputStream;

/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */

public class RemoteDebugProcessHandler extends ProcessHandler{
  private final Project myProject;

  public RemoteDebugProcessHandler(Project project) {
    myProject = project;
  }

  public void startNotify() {
    DebugProcess debugProcess = DebuggerManager.getInstance(myProject).getDebugProcess(this);
    debugProcess.addDebugProcessListener(new DebugProcessAdapter() {
      //executed in manager thread
      public void processDetached(DebugProcess process) {
        notifyProcessDetached();
      }
    });
    super.startNotify();
  }

  protected void destroyProcessImpl() {
    DebugProcess debugProcess = DebuggerManager.getInstance(myProject).getDebugProcess(this);
    if(debugProcess != null) {
      debugProcess.stop(true);
    }
  }

  protected void detachProcessImpl() {
    DebugProcess debugProcess = DebuggerManager.getInstance(myProject).getDebugProcess(this);
    if(debugProcess != null) {
      debugProcess.stop(false);
    }
  }

  public boolean detachIsDefault() {
    return true;
  }

  public OutputStream getProcessInput() {
    return null;
  }
}
