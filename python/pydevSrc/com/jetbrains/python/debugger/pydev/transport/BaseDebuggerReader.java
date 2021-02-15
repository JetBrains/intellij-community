// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.debugger.pydev.transport;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.io.BaseOutputReader;
import com.jetbrains.python.debugger.pydev.RemoteDebugger;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.concurrent.Future;

/**
 * @author Alexander Koshevoy
 */
public abstract class BaseDebuggerReader extends BaseOutputReader {
  private static final Logger LOG = Logger.getInstance(BaseDebuggerReader.class);

  @NotNull private final RemoteDebugger myDebugger;
  @NotNull private StringBuilder myTextBuilder = new StringBuilder();

  public BaseDebuggerReader(@NotNull InputStream inputStream, @NotNull Charset charset, @NotNull RemoteDebugger debugger) {
    super(inputStream, charset);
    myDebugger = debugger;
  }

  @NotNull
  protected RemoteDebugger getDebugger() {
    return myDebugger;
  }

  @Override
  protected void doRun() {
    try {
      while (true) {
        boolean read = readAvailableBlocking();

        if (!read) {
          break;
        }
        else {
          if (isStopped) {
            break;
          }

          TimeoutUtil.sleep(mySleepingPolicy.getTimeToSleep(true));
        }
      }
    }
    catch (Exception e) {
      onCommunicationError();
    }
    finally {
      close();
      onExit();
    }
  }

  protected abstract void onExit();

  protected abstract void onCommunicationError();

  @NotNull
  @Override
  protected Future<?> executeOnPooledThread(@NotNull Runnable runnable) {
    return ApplicationManager.getApplication().executeOnPooledThread(runnable);
  }

  @Override
  protected void close() {
    try {
      super.close();
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  @Override
  public void stop() {
    super.stop();
    close();
  }

  @Override
  protected void onTextAvailable(@NotNull String text) {
    myTextBuilder.append(text);
    if (text.contains("\n")) {
      String[] lines = myTextBuilder.toString().split("\n");
      myTextBuilder = new StringBuilder();

      if (!text.endsWith("\n")) {
        myTextBuilder.append(lines[lines.length - 1]);
        lines = Arrays.copyOf(lines, lines.length - 1);
      }

      for (String line : lines) {
        myDebugger.processResponse(line + "\n");
      }
    }
  }
}
