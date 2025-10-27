// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.common.tools.spi

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.python.common.tools.ToolId
import javax.swing.Icon

interface ToolIdToIconMapper {
  val id: ToolId
  val icon: Icon

  /**
   * Class with icons (jewel requires it)
   */
  val clazz: Class<*>

  companion object {
    internal val EP = ExtensionPointName.create<ToolIdToIconMapper>("com.intellij.python.common.toolToIconMapper")
  }
}