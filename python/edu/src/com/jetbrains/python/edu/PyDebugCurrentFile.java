package com.jetbrains.python.edu;

import com.intellij.execution.actions.RunContextAction;
import com.intellij.openapi.actionSystem.AnAction;
import com.jetbrains.python.edu.debugger.PyEduDebugExecutor;
import org.jetbrains.annotations.Nullable;

public class PyDebugCurrentFile implements PyExecuteFileExtensionPoint {

  @Nullable
  @Override
  public AnAction getRunAction() {
    return new RunContextAction(PyEduDebugExecutor.getInstance());
  }
}

