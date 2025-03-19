package com.jetbrains.performancePlugin.commands;

import com.sampullara.cli.Argument;

public class FindUsagesArguments {
  @Argument
  public String position;

  @Argument
  public String scope = "All Places";

  @Argument
  public String expectedName;
}
