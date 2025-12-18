// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.impl.uv.common.impl

import com.intellij.python.common.tools.ToolId
import com.intellij.python.common.tools.spi.ToolIdToIconMapper
import com.intellij.python.community.impl.uv.common.UV_TOOL_ID
import com.intellij.python.community.impl.uv.common.icons.PythonCommunityImplUVCommonIcons
import javax.swing.Icon

internal class UvToolIdMapper : ToolIdToIconMapper {
  override val id: ToolId = UV_TOOL_ID
  override val icon: Icon = PythonCommunityImplUVCommonIcons.UV
  override val clazz: Class<*> = PythonCommunityImplUVCommonIcons::class.java
}