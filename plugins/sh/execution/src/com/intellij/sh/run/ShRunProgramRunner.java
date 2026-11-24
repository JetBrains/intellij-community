// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.run;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionManager;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.RunnerSettings;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.newvfs.ManagingFS;
import com.intellij.openapi.wm.ToolWindowId;
import org.jetbrains.annotations.NotNull;

import static com.intellij.execution.runners.DefaultProgramRunnerKt.showRunContent;

// The module loads only where intellij.platform.backend.baseline exists: a monolith or a light monolith process.
@SuppressWarnings("SplitModeApiUsage")
final class ShRunProgramRunner implements ProgramRunner<RunnerSettings> {
  @Override
  public @NotNull String getRunnerId() {
    return "shRunRunner";
  }

  @Override
  public boolean canRun(@NotNull String executorId, @NotNull RunProfile profile) {
    // ToolWindowId.DEBUG is the id of DefaultDebugExecutor, whose class needs the debugger module, absent in Light
    return (DefaultRunExecutor.EXECUTOR_ID.equals(executorId) || ToolWindowId.DEBUG.equals(executorId))
           && profile instanceof ShRunConfiguration;
  }

  @Override
  public void execute(@NotNull ExecutionEnvironment environment) throws ExecutionException {
    ExecutionManager.getInstance(environment.getProject()).startRunProfile(environment, state -> {
      FileDocumentManager.getInstance().saveAllDocuments();
      ManagingFS.getInstance().flushPendingUpdatesOrNotify();
      return showRunContent(state.execute(environment.getExecutor(), this), environment);
    });
  }
}