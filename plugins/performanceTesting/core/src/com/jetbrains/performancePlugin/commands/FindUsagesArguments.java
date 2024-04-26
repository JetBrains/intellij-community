package com.jetbrains.performancePlugin.commands;

import com.sampullara.cli.Argument;

public class FindUsagesArguments {
  @Argument
  String position;

  @Argument
  String scope = "All Places";

  @Argument
  String expectedName;
}
