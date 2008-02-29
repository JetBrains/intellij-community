package com.jetbrains.python.run;

import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.runners.DefaultProgramRunner;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class PythonRunner extends DefaultProgramRunner {

  @NotNull
  public String getRunnerId() {
    return "PythonRunner";
  }

  public boolean canRun(@NotNull final String executorId, @NotNull final RunProfile profile) {
    return executorId.equals(DefaultRunExecutor.EXECUTOR_ID) && profile instanceof PythonRunConfiguration;
  }

}
