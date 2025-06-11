package com.intellij.sh.run;

import org.jetbrains.annotations.NotNull;

public interface ShDefaultShellPathProvider {
  /**
   * @deprecated should be converted to suspend API (IJPL-191352)
   */
  @SuppressWarnings("DeprecatedIsStillUsed")
  @Deprecated
  @NotNull String getDefaultShell();
}
