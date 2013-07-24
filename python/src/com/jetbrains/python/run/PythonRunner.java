package com.jetbrains.python.run;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.runners.DefaultProgramRunner;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.RunContentBuilder;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
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
    return executorId.equals(DefaultRunExecutor.EXECUTOR_ID) && profile instanceof AbstractPythonRunConfiguration;
  }

  @Override
  protected RunContentDescriptor doExecute(
    Project project,
    RunProfileState state,
    RunContentDescriptor contentToReuse,
    ExecutionEnvironment env
  ) throws ExecutionException {
    FileDocumentManager.getInstance().saveAllDocuments();

    ExecutionResult executionResult;
    RunProfile profile = env.getRunProfile();
    if (state instanceof PythonCommandLineState && profile instanceof CommandLinePatcher) {
      executionResult = ((PythonCommandLineState)state).execute(env.getExecutor(), (CommandLinePatcher)profile);
    }
    else {
      executionResult = state.execute(env.getExecutor(), this);
    }
    if (executionResult == null) return null;

    final RunContentBuilder contentBuilder = new RunContentBuilder(this, executionResult, env);
    return contentBuilder.showRunContent(contentToReuse);
  }
}
