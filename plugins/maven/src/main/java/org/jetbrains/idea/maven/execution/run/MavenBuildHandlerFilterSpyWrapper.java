// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.execution.run;

import com.intellij.build.process.BuildProcessHandler;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessListener;
import com.intellij.openapi.Disposable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.OutputStream;

/* this class is needed to implement build process handler and support running delegate builds*/
public class MavenBuildHandlerFilterSpyWrapper extends BuildProcessHandler {
  private final ProcessHandler myOriginalHandler;

  public MavenBuildHandlerFilterSpyWrapper(ProcessHandler original) {
    myOriginalHandler = original;
  }


  @Override
  public void destroyProcess() {
    myOriginalHandler.destroyProcess();
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
  public String getExecutionName() {
    return "Maven build";
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
    myOriginalHandler.addProcessListener(filtered(listener));
  }

  @Override
  public void addProcessListener(final @NotNull ProcessListener listener, @NotNull Disposable parentDisposable) {
    myOriginalHandler.addProcessListener(filtered(listener), parentDisposable);
  }

  private ProcessListener filtered(ProcessListener listener) {
    return new ProcessListenerWithFilteredSpyOutput(listener, this);
  }
}
