package com.jetbrains.python.edu.debugger;

import com.intellij.execution.Executor;
import com.intellij.execution.ExecutorRegistry;
import com.intellij.execution.executors.DefaultDebugExecutor;
import org.jetbrains.annotations.NotNull;

public class PyEduDebugExecutor extends DefaultDebugExecutor {
  public static final String ID = "EduExecutor";

  @NotNull
  @Override
  public String getId() {
    return ID;
  }

  public static Executor getInstance() {
    return ExecutorRegistry.getInstance().getExecutorById(ID);
  }

  @Override
  public String getContextActionId() {
    return "EduDebug";
  }

  @NotNull
  @Override
  public String getStartActionText() {
    return "Step Through";
  }
}
