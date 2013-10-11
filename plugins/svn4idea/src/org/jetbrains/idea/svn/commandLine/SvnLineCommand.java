/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.openapi.vcs.LineHandlerHelper;
import com.intellij.openapi.vcs.LineProcessEventListener;
import com.intellij.util.EventDispatcher;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 1/25/12
 * Time: 4:05 PM
 *
 * honestly stolen from GitLineHandler
 */
public class SvnLineCommand extends SvnCommand {

  // kept for exact text
  //public static final String CLIENT_CERTIFICATE_FILENAME = "Client certificate filename:";
  /**
   * the partial line from stdout stream
   */
  private final StringBuilder myStdoutLine = new StringBuilder();
  /**
   * the partial line from stderr stream
   */
  private final StringBuilder myStderrLine = new StringBuilder();
  private final EventDispatcher<LineProcessEventListener> myLineListeners;
  private final AtomicReference<Integer> myExitCode;
  private final StringBuffer myErr;
  private final StringBuffer myStdOut;

  public SvnLineCommand(File workingDirectory, @NotNull SvnCommandName commandName, @NotNull @NonNls String exePath, File configDir) {
    super(workingDirectory, commandName, exePath, configDir);
    myLineListeners = EventDispatcher.create(LineProcessEventListener.class);
    myExitCode = new AtomicReference<Integer>();
    myErr = new StringBuffer();
    myStdOut = new StringBuffer();
  }

  @Override
  protected void processTerminated(int exitCode) {
    // force newline
    if (myStdoutLine.length() != 0) {
      onTextAvailable("\n\r", ProcessOutputTypes.STDOUT);
    }
    else if (myStderrLine.length() != 0) {
      onTextAvailable("\n\r", ProcessOutputTypes.STDERR);
    }
  }

  @Override
  protected void onTextAvailable(String text, Key outputType) {
    Iterator<String> lines = LineHandlerHelper.splitText(text).iterator();
    if (ProcessOutputTypes.STDOUT == outputType) {
      notifyLines(outputType, lines, myStdoutLine);
    }
    else if (ProcessOutputTypes.STDERR == outputType) {
      notifyLines(outputType, lines, myStderrLine);
    }
  }

  private void notifyLines(final Key outputType, final Iterator<String> lines, final StringBuilder lineBuilder) {
    if (!lines.hasNext()) return;
    if (lineBuilder.length() > 0) {
      lineBuilder.append(lines.next());
      if (lines.hasNext()) {
        // line is complete
        final String line = lineBuilder.toString();
        notifyLine(line, outputType);
        lineBuilder.setLength(0);
      }
    }
    while (true) {
      String line = null;
      if (lines.hasNext()) {
        line = lines.next();
      }

      if (lines.hasNext()) {
        notifyLine(line, outputType);
      }
      else {
        if (line != null && line.length() > 0) {
          lineBuilder.append(line);
        }
        break;
      }
    }
  }

  private void notifyLine(final String line, final Key outputType) {
    String trimmed = LineHandlerHelper.trimLineSeparator(line);
    myLineListeners.getMulticaster().onLineAvailable(trimmed, outputType);
  }

  public void addLineListener(LineProcessEventListener listener) {
    myLineListeners.addListener(listener);
    super.addListener(listener);
  }

  public StringBuffer getError() {
    return myErr;
  }

  public StringBuffer getStdOut() {
    return myStdOut;
  }

  public Integer getExitCodeReference() {
    return myExitCode.get();
  }

  public void setExitCodeReference(int value) {
    myExitCode.set(value);
  }
}
