// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.execution.run;

import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessListener;

public interface MavenSpyFilter {
  default ProcessListener filtered(ProcessListener listener, ProcessHandler processHandler) {
    return new ProcessListenerWithFilteredSpyOutput(listener, processHandler);
  }
}
