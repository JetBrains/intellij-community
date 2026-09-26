// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.run;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

/**
 * A run configuration that executes a single script file, and whose script may therefore carry a PEP 723 metadata
 * block declaring the dependencies to run it with.
 * <p>
 * Obtained from {@link AbstractPythonRunConfiguration#asBareScriptConfiguration()}, which is what decides whether a
 * configuration runs a bare script at all.
 */
@ApiStatus.Internal
public interface PyBareScriptConfiguration {
  /**
   * The script to hand to the run tool as a PEP 723 script, or {@code null} to run this configuration the ordinary way.
   */
  @Nullable Path getInlineScriptTarget();
}
