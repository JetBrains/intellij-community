package com.jetbrains.performancePlugin.commands;

import com.sampullara.cli.Argument;

/**
 * Warning! This dto used with sampullara lib(command extractor). This lib doesn't work properly with kotlin
 * Properties won't be injected when Args.parse
 */
public class OpenFileCommandOptions {
  @Argument
  public Long timeout = 0L;

  @Argument
  public Boolean suppressErrors = false;

  @Argument(required = true)
  public String file = "";

  @Argument(alias = "dsa")
  public Boolean disableCodeAnalysis = false;
}
