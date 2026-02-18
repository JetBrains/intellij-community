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
   */
  void run(@NotNull Project project,
           @NotNull String command,
           @NotNull String workingDirectory,
           @NotNull @NlsContexts.TabTitle String title,
           boolean activateToolWindow);

  @RequiresEdt(generateAssertion = false)
  boolean isAvailable(@NotNull Project project);
}
