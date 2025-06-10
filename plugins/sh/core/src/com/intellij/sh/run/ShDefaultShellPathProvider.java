package com.intellij.sh.run;

import org.jetbrains.annotations.NotNull;

public interface ShDefaultShellPathProvider {
  /**
   * @deprecated should be converted to suspend API
   */
  @Deprecated
  @NotNull String getDefaultShell();
}
