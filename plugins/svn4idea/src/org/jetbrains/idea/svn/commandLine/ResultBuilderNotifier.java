package org.jetbrains.idea.svn.commandLine;

import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vcs.LineHandlerHelper;
import org.jetbrains.annotations.NotNull;

import java.util.Iterator;

/**
* @author Konstantin Kolosovsky.
*/
public class ResultBuilderNotifier extends ProcessAdapter {

  /**
   * the partial line from stdout stream
   */
  @NotNull private final StringBuilder myStdoutLine = new StringBuilder();
  /**
   * the partial line from stderr stream
   */
  @NotNull private final StringBuilder myStderrLine = new StringBuilder();

  @NotNull private final LineCommandListener myResultBuilder;

  public ResultBuilderNotifier(@NotNull LineCommandListener resultBuilder) {
    myResultBuilder = resultBuilder;
  }

  public void processTerminated(@NotNull final ProcessEvent event) {
    try {
      forceNewLine();
    } finally {
      myResultBuilder.processTerminated(event.getExitCode());
    }
  }

  private void forceNewLine() {
    if (myStdoutLine.length() != 0) {
      onTextAvailable("\n\r", ProcessOutputTypes.STDOUT);
    }
    else if (myStderrLine.length() != 0) {
      onTextAvailable("\n\r", ProcessOutputTypes.STDERR);
    }
  }

  public void onTextAvailable(@NotNull final ProcessEvent event, @NotNull final Key outputType) {
    onTextAvailable(event.getText(), outputType);
  }

  private void onTextAvailable(final String text, final Key outputType) {
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
    myResultBuilder.onLineAvailable(LineHandlerHelper.trimLineSeparator(line), outputType);
  }
}
