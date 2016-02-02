package com.jetbrains.python.edu;

import com.intellij.execution.actions.RunContextAction;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.project.Project;
import com.intellij.util.PlatformUtils;
import com.jetbrains.python.edu.debugger.PyEduDebugExecutor;
import org.jetbrains.annotations.Nullable;

public class PyDebugCurrentFile implements PyExecuteFileExtensionPoint {

  @Nullable
  @Override
  public AnAction getRunAction() {
    if (!PlatformUtils.isPyCharmEducational()) {
      return null;
    }
    return new RunContextAction(PyEduDebugExecutor.getInstance());
  }

  @Override
  public boolean accept(Project project) {
    return PlatformUtils.isPyCharmEducational();
  }
}

