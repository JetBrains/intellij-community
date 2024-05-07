package com.jetbrains.performancePlugin.commands;

import com.sampullara.cli.Argument;

public class WaitForFinishedCodeAnalysisOptions {
  @Argument
  public Long timeout = 0L;

}
