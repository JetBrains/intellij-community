// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.env;

import com.intellij.openapi.project.Project;
import com.intellij.python.sdk.backend.PythonInterpreter;
import com.intellij.util.text.SemVer;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * Abstraction for a Python test environment that can be used to run tests.
 * Implementations can represent either pre-built environments (legacy) or provider-based environments.
 */
public interface PyTestEnvironment extends AutoCloseable {
  /**
   * Get the description of this environment (e.g., root path or environment type).
   */
  @NotNull
  String getDescription();

  /**
   * Get the tags associated with this environment (e.g., "python3.12", "django", "pytest").
   */
  @NotNull
  Set<String> getTags();

  /**
   * Prepares the environment and adds its interpreter to the registry of {@code project}, for running tests. Remove it
   * with {@link #removeInterpreter}.
   *
   * @return the interpreter to use for running tests
   */
  @NotNull
  PythonInterpreter prepareInterpreter(@NotNull Project project);

  /** Removes an interpreter that {@link #prepareInterpreter} added to {@code project}. */
  void removeInterpreter(@NotNull Project project, @NotNull PythonInterpreter interpreter);

  /**
   * Clean up resources associated with this environment.
   */
  @Override
  void close() throws Exception;

  SemVer getPythonVersion();
}
