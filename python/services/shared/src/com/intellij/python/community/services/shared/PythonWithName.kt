// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.services.shared

import org.jetbrains.annotations.Nls

interface PythonWithName {
  /**
   * Name can be displayed to the end user
   */
  suspend fun getReadableName(): @Nls String
}