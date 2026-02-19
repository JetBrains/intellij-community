// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.commands;

import com.sampullara.cli.Argument;

public class EvaluateExpressionArguments implements CommandArguments {
  @Argument
  public String expression = "";
  @Argument
  public Integer performEvaluateCount = 0;
}
