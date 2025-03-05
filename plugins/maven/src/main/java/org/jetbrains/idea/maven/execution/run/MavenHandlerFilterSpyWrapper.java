// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.execution.run;

import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessListener;
import com.intellij.openapi.Disposable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.OutputStream;

class MavenHandlerFilterSpyWrapper extends ProcessHandler implements MavenSpyFilter {
  private final ProcessHandler myOriginalHandler;

  MavenHandlerFilterSpyWrapper(ProcessHandler original) {
    myOriginalHandler = original;
  }

  @Override
  public void detachProcess() {
    myOriginalHandler.detachProcess();
  }

  @Override
  public boolean isProcessTerminated() {
    return myOriginalHandler.isProcessTerminated();
  }

  @Override
  public boolean isProcessTerminating() {
    return myOriginalHandler.isProcessTerminating();
  }

  @Override
  public @Nullable Integer getExitCode() {
    return myOriginalHandler.getExitCode();
  }

  @Override
  protected void destroyProcessImpl() {
    myOriginalHandler.destroyProcess();
  }

  @Override
  protected void detachProcessImpl() {
    myOriginalHandler.detachProcess();
  }

  @Override
  public boolean detachIsDefault() {
    return myOriginalHandler.detachIsDefault();
  }

  @Override
  public @Nullable OutputStream getProcessInput() {
    return myOriginalHandler.getProcessInput();
  }

  @Override
  public void addProcessListener(@NotNull ProcessListener listener) {
    myOriginalHandler.addProcessListener(filtered(listener, this));
  }

  @Override
  public void addProcessListener(final @NotNull ProcessListener listener, @NotNull Disposable parentDisposable) {
    myOriginalHandler.addProcessListener(filtered(listener, this), parentDisposable);
  }
}
