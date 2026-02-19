// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Contract
import java.util.regex.Pattern

@ApiStatus.Internal
object CustomSdkHomePattern {
  /**
   * Note that *\w+.** pattern is not sufficient because we need also the
   * hyphen sign (*-*) for *docker-compose:* scheme.
   * For WSL we use `\\wsl.local\` or `\\wsl$\`.
   * As with a new workspace model paths changed on save, hence we need to support `//wsl` as well
   */
  private val CUSTOM_PYTHON_SDK_HOME_PATH_PATTERN: Pattern = Pattern.compile("^([-a-zA-Z_0-9]{2,}:|\\\\\\\\|//wsl).+")

  /**
   * Returns whether provided Python interpreter path corresponds to custom
   * Python SDK.
   *
   * @param homePath SDK home path
   * @return whether provided Python interpreter path corresponds to custom Python SDK
   */
  @JvmStatic
  @Contract(pure = true)
  @ApiStatus.Internal
  fun isCustomPythonSdkHomePath(homePath: String): Boolean {
    return CUSTOM_PYTHON_SDK_HOME_PATH_PATTERN.matcher(homePath).matches()
  }
}