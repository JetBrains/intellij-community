// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.services.shared

import com.jetbrains.python.PythonInfo

/**
 * Something with python info
 */
interface PythonInfoHolder {
  val pythonInfo: PythonInfo
}