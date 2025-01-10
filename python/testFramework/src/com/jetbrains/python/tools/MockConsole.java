// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.tools;

import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessListener;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.project.Project;

import java.util.HashMap;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CountDownLatch;


/**
 * Console for tests. It can be attached to process, it then allows you to {@link #waitForProcessToEnd()}
 * and get its output as {@link #getOutput()}
 *
 * @author Ilya.Kazakevich
 */
@ApiStatus.Internal
public final class MockConsole extends ConsoleViewImpl implements ProcessListener {
  /**
   * Output stored here
   */
  @NotNull
  private final Map<ConsoleViewContentType, String> myOutput = new HashMap<>();

  /**
   * sync aid to wait for process
   */
  private final CountDownLatch myBarrier = new CountDownLatch(1);

  /**
   * @param project project
   */
  public MockConsole(@NotNull final Project project) {
    super(project, false);
    for (final ConsoleViewContentType type : ConsoleViewContentType.OUTPUT_TYPES) {
      // Fill map with allowed output types
      myOutput.put(type, "");
    }
  }

  @Override
  public void print(@NotNull final String text, @NotNull final ConsoleViewContentType contentType) {
    super.print(text, contentType);
    myOutput.put(contentType, myOutput.get(contentType) + '\n' + text);
  }

  @Override
  public void attachToProcess(final @NotNull ProcessHandler processHandler) {
    super.attachToProcess(processHandler);
    processHandler.addProcessListener(this);
  }

  /**
   * @return map of [output_type, output]
   */
  @NotNull
public   Map<ConsoleViewContentType, String> getOutput() {
    return Collections.unmodifiableMap(myOutput);
  }

  @Override
  public void processTerminated(@NotNull final ProcessEvent event) {
    myBarrier.countDown();
  }

  /**
   * Waits for running (attached) process to end
   *
   * @throws InterruptedException waiting interrupted
   */
 public void waitForProcessToEnd() throws InterruptedException {
    myBarrier.await();
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }
}
