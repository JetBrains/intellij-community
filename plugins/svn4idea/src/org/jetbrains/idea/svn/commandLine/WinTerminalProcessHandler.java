/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author Konstantin Kolosovsky.
 */
public class WinTerminalProcessHandler extends TerminalProcessHandler {

  // see http://en.wikipedia.org/wiki/ANSI_escape_code
  private static final String NON_CSI_ESCAPE_CODE = "\u001B.[@-_]";
  private static final String CSI_ESCAPE_CODE = "\u001B\\[(.*?)[@-~]";

  public WinTerminalProcessHandler(@NotNull Process process, @NotNull String commandLine, boolean forceUtf8, boolean forceBinary) {
    super(process, commandLine, forceUtf8, forceBinary);
  }

  @Override
  protected boolean processHasSeparateErrorStream() {
    return true;
  }

  @NotNull
  @Override
  protected String filterCombinedText(@NotNull String currentLine) {
    // for windows platform output is assumed in format suitable for terminal emulator
    // for instance, same text could be returned twice with '\r' symbol in between (so in emulator output we'll still see correct
    // text without duplication)
    // because of this we manually process '\r' occurrences to get correct output
    return removeAllBeforeCaretReturn(currentLine);
  }

  @NotNull
  @Override
  protected String filterText(@NotNull String text) {
    // filter terminal escape codes - they are presented in the output for windows platform
    text = text.replaceAll(CSI_ESCAPE_CODE, "").replaceAll(NON_CSI_ESCAPE_CODE, "");
    // trim leading '\r' symbols - as they break xml parsing logic
    text = StringUtil.trimLeading(text, '\r');

    return text;
  }

  @NotNull
  @Override
  protected Key resolveOutputType(@NotNull String line, @NotNull Key outputType) {
    return outputType;
  }

  @NotNull
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
}
