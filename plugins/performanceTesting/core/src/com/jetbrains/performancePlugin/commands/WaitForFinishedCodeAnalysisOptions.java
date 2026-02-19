// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.commands;

import com.sampullara.cli.Argument;

public class WaitForFinishedCodeAnalysisOptions {
  @Argument
  public Long timeout = 0L;

}
