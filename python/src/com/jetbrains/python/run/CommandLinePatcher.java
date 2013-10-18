package com.jetbrains.python.run;

import com.intellij.execution.configurations.GeneralCommandLine;

/**
 * @author yole
 */
public interface CommandLinePatcher {
  void patchCommandLine(GeneralCommandLine commandLine);
}
