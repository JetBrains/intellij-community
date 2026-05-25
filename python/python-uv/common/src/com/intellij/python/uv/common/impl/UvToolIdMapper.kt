// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.uv.common.impl

import com.intellij.python.common.tools.ToolId
import com.intellij.python.common.tools.spi.ToolIdToIconMapper
import com.intellij.python.uv.common.UV_TOOL_ID
import com.intellij.python.uv.common.icons.PythonUvCommonIcons
import javax.swing.Icon

internal class UvToolIdMapper : ToolIdToIconMapper {
  override val id: ToolId = UV_TOOL_ID
  override val icon: Icon = PythonUvCommonIcons.UV
  override val clazz: Class<*> = PythonUvCommonIcons::class.java
}