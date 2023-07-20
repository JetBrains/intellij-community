package com.jetbrains.performancePlugin.commands;

import com.sampullara.cli.Argument;

public class OpenFileCommandOptions {
  @Argument
  public Long timeout = 0L;

  @Argument
  public Boolean suppressErrors = false;

  @Argument(required = true)
  public String file = "";
}
