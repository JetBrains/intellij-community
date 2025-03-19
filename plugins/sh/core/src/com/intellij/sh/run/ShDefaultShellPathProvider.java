package com.intellij.sh.run;

import org.jetbrains.annotations.NotNull;

public interface ShDefaultShellPathProvider {
  @NotNull String getDefaultShell();
}
