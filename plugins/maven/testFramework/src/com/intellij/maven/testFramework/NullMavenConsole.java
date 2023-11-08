// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.maven.testFramework;

import org.jetbrains.idea.maven.execution.MavenExecutionOptions;
import org.jetbrains.idea.maven.project.MavenConsole;

public class NullMavenConsole extends MavenConsole {
  public NullMavenConsole() {
    super(MavenExecutionOptions.LoggingLevel.DISABLED);
  }

  @Override
  protected void doPrint(String text, MavenConsole.OutputType type) {
  }
}
