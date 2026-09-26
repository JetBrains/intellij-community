// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.session;

import org.jetbrains.annotations.ApiStatus;

/**
 * The Shell functions, provided by shell integrations (i.e. command-block-support)
 */
@ApiStatus.Internal
public enum ShellIntegrationFunctions {
  GET_ENVIRONMENT("__jetbrains_intellij_get_environment"),
  /**
   * Return: list of aliases defined in the current session.
   * The format is Shell-specific.
   */
  GET_ALIASES("__jetbrains_intellij_get_aliases"),
  GET_DIRECTORY_FILES("__jetbrains_intellij_get_directory_files"),
  /**
   * POWERSHELL ONLY
   * argument: 1. escapedCommand
   * argument: 2. caretOffset
   */
  GET_COMPLETIONS("__JetBrainsIntellijGetCompletions")
  ;

  ShellIntegrationFunctions(String functionName) {
    myFunctionName = functionName;
  }

  private final String myFunctionName;

  public String getFunctionName() {
    return myFunctionName;
  }

}
