// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.run;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import org.jetbrains.annotations.NotNull;

public interface ShRunner {
  /**
   * Should run a given command in the integrated terminal.
   * The implementation can make it asynchronously,
   * there is no guarantee that the result will be immediately visible after completion of this method.
   * So, it can be called on any thread.
   * <p>
   * The terminal tab is created by the frontend of the current session, so in remote development the request travels to the
   * client that owns the current {@link com.intellij.codeWithMe.ClientId}.
   *
   * @param command          the shell line to execute, already quoted for the target shell
   * @param workingDirectory the directory to start the shell in: spelled as the project's environment sees it (an {@code EelPath}
   *                         string) when it belongs to that environment, otherwise a nio path of the sending machine that the
   *                         terminal can route on its own (a WSL directory open in a local project)
   */
  void run(@NotNull Project project,
           @NotNull String command,
           @NotNull String workingDirectory,
           @NotNull @NlsContexts.TabTitle String title,
           boolean activateToolWindow);

  @RequiresEdt(generateAssertion = false)
  boolean isAvailable(@NotNull Project project);
}
