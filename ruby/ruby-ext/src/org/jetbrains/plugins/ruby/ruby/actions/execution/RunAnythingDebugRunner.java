package org.jetbrains.plugins.ruby.ruby.actions.execution;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.xdebugger.XDebugSession;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.ruby.ruby.debugger.DebuggableRunProfile;
import org.jetbrains.plugins.ruby.ruby.debugger.RubyDebugRunner;

public class RunAnythingDebugRunner extends RubyDebugRunner {
  @Override
  protected boolean isMyProfile(@NotNull RunProfile profile) {
    return profile instanceof DebuggableRunProfile;
  }

  @NotNull
  @Override
  protected XDebugSession createDebugSession(@NotNull RunProfileState state, @NotNull ExecutionEnvironment environment)
    throws ExecutionException {
    return ((DebuggableRunProfile)environment.getRunProfile()).createDebugSession(state, environment);
  }
}
