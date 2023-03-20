// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.terminal;

import com.jediterm.core.util.TermSize;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @deprecated use {@link ShellStartupOptions} instead
 */
@Deprecated(forRemoval = true)
public class TerminalProcessOptions {

  private final String myWorkingDirectory;
  private final TermSize myInitialTermSize;

  public TerminalProcessOptions(@Nullable String workingDirectory, @Nullable Integer initialColumns, @Nullable Integer initialRows) {
    this(workingDirectory, initialColumns != null && initialRows != null ? new TermSize(initialColumns, initialRows) : null);
  }

  public TerminalProcessOptions(@Nullable String workingDirectory, @Nullable TermSize initialTermSize) {
    myWorkingDirectory = workingDirectory;
    myInitialTermSize = initialTermSize;
  }

  public @Nullable String getWorkingDirectory() {
    return myWorkingDirectory;
  }

  public @Nullable TermSize getInitialTermSize() {
    return myInitialTermSize;
  }

  public @NotNull ShellStartupOptions toStartupOptions() {
    return new ShellStartupOptions.Builder().workingDirectory(myWorkingDirectory).initialTermSize(myInitialTermSize).build();
  }
}
