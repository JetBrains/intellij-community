// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.run;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.NotNull;

public interface ShRunner {
  void run(@NotNull Project project,
           @NotNull String command,
           @NotNull String workingDirectory,
           @NotNull @NlsContexts.TabTitle String title,
           boolean activateToolWindow);

  boolean isAvailable(@NotNull Project project);
}
