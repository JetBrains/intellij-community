package com.jetbrains.python.packaging;

import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

public final class IndicatedProcessOutputListener extends ProcessAdapter {
  private final @NotNull ProgressIndicator myIndicator;

  public IndicatedProcessOutputListener(@NotNull ProgressIndicator indicator) {
    myIndicator = indicator;
  }

  @Override
  public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {
    if (outputType == ProcessOutputTypes.STDOUT || outputType == ProcessOutputTypes.STDERR) {
      for (String line : StringUtil.splitByLines(event.getText())) {
        final @NlsSafe String trimmed = line.trim();
        if (isMeaningfulOutput(trimmed)) {
          myIndicator.setText2(trimmed);
        }
      }
    }
  }

  private static boolean isMeaningfulOutput(@NotNull String trimmed) {
    return trimmed.length() > 3;
  }
}
