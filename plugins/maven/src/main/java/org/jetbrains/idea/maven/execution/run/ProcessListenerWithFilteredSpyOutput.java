// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.execution.run;

import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessListener;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.registry.Registry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.execution.MavenSimpleConsoleEventsBuffer;
import org.jetbrains.idea.maven.utils.MavenLog;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

//this listener used for proper console output in buildtools - to hide or show spy log in process out, it is unrelated to build events processing
public class ProcessListenerWithFilteredSpyOutput implements ProcessListener {
  private static final Pattern CMD_END_PATTERN
    = Pattern.compile("\\((.)\\s*/\\s*[^)]\\)\\?");
  private final ProcessListener myListener;
  private final boolean myIsWindowsCmd;
  private final MavenSimpleConsoleEventsBuffer mySimpleConsoleEventsBuffer;
  private final ProcessHandler myProcessHandler;
  private final AtomicBoolean myTerminateEventReceived = new AtomicBoolean(false);


  ProcessListenerWithFilteredSpyOutput(ProcessListener listener,
                                       ProcessHandler processHandler,
                                       boolean withLoggingOutputStream,
                                       boolean isWindowsCmd) {
    myListener = listener;
    myProcessHandler = processHandler;
    myIsWindowsCmd = isWindowsCmd;
    mySimpleConsoleEventsBuffer = new MavenSimpleConsoleEventsBuffer.Builder(
      (l, k) -> myListener.onTextAvailable(new ProcessEvent(processHandler, l), k))
      .withSpyOutput(Registry.is("maven.spy.events.debug"))
      .withLoggingOutputStream(withLoggingOutputStream)
      .withHidingCmdExitQuestion(isWindowsCmd)
      .build();
  }

  @Override
  public void startNotified(@NotNull ProcessEvent event) {
    myListener.startNotified(event);
  }

  @Override
  public void processTerminated(@NotNull ProcessEvent event) {
    myListener.processTerminated(event);
  }

  @Override
  public void processWillTerminate(@NotNull ProcessEvent event, boolean willBeDestroyed) {
    myTerminateEventReceived.set(true);
    myListener.processWillTerminate(event, willBeDestroyed);
  }

  @Override
  public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {
    if (myTerminateEventReceived.get() && myIsWindowsCmd) {
      if (tryToExtractYesButton(event.getText()) && !Registry.is("maven.spy.events.debug")) return;
    }
    mySimpleConsoleEventsBuffer.addText(event.getText(), outputType);
  }

  private boolean tryToExtractYesButton(@NlsSafe String text) {
    Matcher m = CMD_END_PATTERN.matcher(text);
    if (!m.find()) {
      return false;
    }

    var yesKey = m.group(1);
    MavenLog.LOG.debug("Exit message from bat script: " + text + "  extracted yesCode: " + yesKey);
    sayYes(yesKey);
    return true;
  }

  private void sayYes(String yes) {
    try {
      OutputStream input = myProcessHandler.getProcessInput();
      if (input == null) {
        MavenLog.LOG.warn("Cannot say yes to exit because process unput is null");
        return;
      }

      input.write((yes + "\r\n").getBytes(StandardCharsets.UTF_8));
    }
    catch (IOException e) {
      MavenLog.LOG.warn("exception while saying yes to exit:", e);
    }
  }
}
