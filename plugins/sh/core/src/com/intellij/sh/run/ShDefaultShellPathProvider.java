package com.intellij.sh.run;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public interface ShDefaultShellPathProvider {
  /**
   * @deprecated should be converted to suspend API (IJPL-191352)
   */
  @SuppressWarnings("DeprecatedIsStillUsed")
  @Deprecated
  @NotNull String getDefaultShell();
}
