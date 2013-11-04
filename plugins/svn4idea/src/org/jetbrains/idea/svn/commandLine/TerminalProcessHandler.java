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

import com.intellij.execution.process.CapturingProcessAdapter;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Konstantin Kolosovsky.
 */
public class TerminalProcessHandler extends OSProcessHandler {

  // see http://en.wikipedia.org/wiki/ANSI_escape_code
  private static final String NON_CSI_ESCAPE_CODE = "\u001B.[@-_]";
  private static final String CSI_ESCAPE_CODE = "\u001B\\[(.*?)[@-~]";

  private final List<InteractiveCommandListener> myInteractiveListeners = ContainerUtil.createLockFreeCopyOnWriteList();
  private final CapturingProcessAdapter terminalOutputCapturer = new CapturingProcessAdapter();

  private final StringBuilder outputLine = new StringBuilder();
  private final StringBuilder errorLine = new StringBuilder();

  public TerminalProcessHandler(@NotNull Process process) {
    super(process);
  }

  public void addInteractiveListener(@NotNull InteractiveCommandListener listener) {
    myInteractiveListeners.add(listener);
  }

  @Override
  public void notifyTextAvailable(String text, Key outputType) {
    terminalOutputCapturer.onTextAvailable(new ProcessEvent(this, text), outputType);

    // filter terminal escape codes - they are presented in the output for windows platform
    String filteredText = text.replaceAll(CSI_ESCAPE_CODE, "").replaceAll(NON_CSI_ESCAPE_CODE, "");
    // trim leading '\r' symbols - as they break xml parsing logic
    filteredText = StringUtil.trimLeading(filteredText);

    if (!StringUtil.isEmpty(filteredText)) {
      StringBuilder lastLine = getLastLineFor(outputType);
      String currentLine = lastLine.append(filteredText).toString();
      lastLine.setLength(0);

      // check if current line presents some interactive output
      boolean handled = false;
      for (InteractiveCommandListener listener : myInteractiveListeners) {
        handled |= listener.handlePrompt(currentLine, outputType);
      }

      if (!handled) {
        notify(currentLine, outputType, lastLine);
      }
    }
  }

  private void notify(@NotNull String text, @NotNull Key outputType, @NotNull StringBuilder lastLine) {
    // for windows platform output is assumed in format suitable for terminal emulator
    // for instance, same text could be returned twice with '\r' symbol in between (so in emulator output we'll still see correct
    // text without duplication)
    // because of this we manually process '\r' occurrences to get correct output
    text = removeAllBeforeCaretReturn(text);

    // text is not more than one line - either one line or part of the line
    if (StringUtil.endsWith(text, "\n")) {
      // we have full line - notify listeners
      super.notifyTextAvailable(text, outputType);
    }
    else {
      // save line part to lastLine
      lastLine.append(text);
    }
  }

  private static String removeAllBeforeCaretReturn(@NotNull String line) {
    int caretReturn = line.lastIndexOf("\r");

    while (caretReturn >= 0) {
      if (caretReturn + 1 < line.length() && line.charAt(caretReturn + 1) != '\n') {
        // next symbol is not '\n' - we should not treat text before found caret return symbol
        line = line.substring(caretReturn + 1);
        break;
      }
      caretReturn = line.lastIndexOf("\r", caretReturn - 1);
    }

    return line;
  }

  @NotNull
  private StringBuilder getLastLineFor(Key outputType) {
    if (ProcessOutputTypes.STDERR.equals(outputType)) {
      return errorLine;
    }
    else if (ProcessOutputTypes.STDOUT.equals(outputType)) {
      return outputLine;
    }
    else {
      throw new IllegalArgumentException("Unknown process output type " + outputType);
    }
  }
}
