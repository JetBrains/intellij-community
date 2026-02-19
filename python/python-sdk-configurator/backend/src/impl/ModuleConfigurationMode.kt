package com.intellij.python.sdkConfigurator.backend.impl

/**
 * SDK for project modules can be configured in two different modes (see PY-86454)
 */
internal enum class ModuleConfigurationMode {
  /**
   * Do not bother user with questions, do not touch filesystem.
   * For one-module project, configure SDK if files (i.e. venv) already exist.
   * For multi-module only set SDK for workspace members if workspace parent SDK is set.
   *
   * In other words: No more than 1 SDK could be created. No files should be created.
   */
  AUTOMATIC,

  /**
   * Show user list of modules without SDK, ask them which to configure, and do so.
   */
  INTERACTIVE
}
