// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pyproject

import org.apache.tuweni.toml.TomlTable

interface PyProjectToolFactory<T> {
  val tables: List<String>
  fun createTool(tables: Map<String, TomlTable?>): T
}