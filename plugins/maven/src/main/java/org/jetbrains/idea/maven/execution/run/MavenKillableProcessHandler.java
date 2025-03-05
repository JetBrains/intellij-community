// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.execution.run;

import com.intellij.execution.process.KillableProcessHandler;
import com.intellij.execution.process.ProcessListener;
import com.intellij.openapi.Disposable;
import com.intellij.util.io.BaseOutputReader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.charset.Charset;
import java.util.Set;

class MavenKillableProcessHandler extends KillableProcessHandler implements MavenSpyFilter {

  MavenKillableProcessHandler(@NotNull Process process,
                                      String commandLine,
                                      @NotNull Charset charset,
                                      @Nullable Set<File> filesToDelete) {
    super(process, commandLine, charset, filesToDelete);
  }

  @Override
  public @NotNull BaseOutputReader.Options readerOptions() {
    return BaseOutputReader.Options.forTerminalPtyProcess();
  }

  @Override
  public void addProcessListener(@NotNull ProcessListener listener) {
    super.addProcessListener(filtered(listener, this));
  }

  @Override
  public void addProcessListener(final @NotNull ProcessListener listener, @NotNull Disposable parentDisposable) {
    super.addProcessListener(filtered(listener, this), parentDisposable);
  }
}
