package com.jetbrains.performancePlugin.commands;

import com.sampullara.cli.Argument;

public class EvaluateExpressionArguments implements CommandArguments {
  @Argument
  public String expression = "";
  @Argument
  public Integer performEvaluateCount = 0;
}
