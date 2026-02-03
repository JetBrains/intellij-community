// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.services.shared

import com.jetbrains.python.PyToolUIInfo

interface UiHolder {
  /**
   * UI hints on how to display this python to the end user: icon, title, etc
   */
  val ui: PyToolUIInfo?
}