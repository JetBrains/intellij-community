// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk.skeletons;

import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessOutputType;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

public interface LineWiseProcessOutputListener {
  class Adapter extends ProcessAdapter {
    private final StringBuilder myStdoutLine = new StringBuilder();
    private final StringBuilder myStderrLine = new StringBuilder();
    private final LineWiseProcessOutputListener myListener;

    public Adapter(@NotNull LineWiseProcessOutputListener listener) {
      myListener = listener;
    }

    @Override
    public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {
      final boolean isStdout = ProcessOutputType.isStdout(outputType);
      final StringBuilder lineBuilder = isStdout ? myStdoutLine : myStderrLine;
      for (String chunk : StringUtil.splitByLinesKeepSeparators(event.getText())) {
        lineBuilder.append(chunk);
        if (StringUtil.isLineBreak(lineBuilder.charAt(lineBuilder.length() - 1))) {
          final String line = lineBuilder.toString();
          if (isStdout) {
            myListener.onStdoutLine(line);
          }
          else {
            myListener.onStderrLine(line);
          }
          lineBuilder.setLength(0);
        }
      }
    }

    @Override
    public void processTerminated(@NotNull ProcessEvent event) {
      if (!myStdoutLine.isEmpty()) {
        myListener.onStdoutLine(myStdoutLine.toString());
      }
      if (!myStderrLine.isEmpty()) {
        myListener.onStderrLine(myStderrLine.toString());
      }
    }
  }

  void onStdoutLine(@NotNull String line);

  void onStderrLine(@NotNull String line);
}
