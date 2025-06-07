// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.svn.commandLine;

import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.ProgressEvent;
import org.jetbrains.idea.svn.api.ProgressTracker;

import java.io.File;
import java.util.concurrent.atomic.AtomicReference;

import static org.jetbrains.idea.svn.api.BaseSvnClient.callHandler;

public class BaseUpdateCommandListener extends LineCommandAdapter {

  private final @NotNull UpdateOutputLineConverter converter;

  private final @Nullable ProgressTracker handler;

  private final @NotNull AtomicReference<SvnBindException> exception;

  public BaseUpdateCommandListener(@NotNull File base, @Nullable ProgressTracker handler) {
    this.handler = handler;
    this.converter = new UpdateOutputLineConverter(base);
    exception = new AtomicReference<>();
  }

  @Override
  public void onLineAvailable(String line, Key outputType) {
    if (ProcessOutputTypes.STDOUT.equals(outputType)) {
      final ProgressEvent event = converter.convert(line);
      if (event != null) {
        beforeHandler(event);
        try {
          callHandler(handler, event);
        }
        catch (SvnBindException e) {
          cancel();
          exception.set(e);
        }
      }
    }
  }

  public void throwWrappedIfException() throws SvnBindException {
    SvnBindException e = exception.get();

    if (e != null) {
      throw e;
    }
  }

  protected void beforeHandler(@NotNull ProgressEvent event) {
  }
}
