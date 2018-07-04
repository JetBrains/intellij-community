/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

/**
 * @author Konstantin Kolosovsky.
 */
public class BaseUpdateCommandListener extends LineCommandAdapter {

  @NotNull
  private final UpdateOutputLineConverter converter;

  @Nullable
  private final ProgressTracker handler;

  @NotNull
  private final AtomicReference<SvnBindException> exception;

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
