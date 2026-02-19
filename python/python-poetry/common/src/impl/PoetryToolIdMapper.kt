// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.impl.poetry.common.impl

import com.intellij.python.common.tools.ToolId
import com.intellij.python.common.tools.spi.ToolIdToIconMapper
import com.intellij.python.community.impl.poetry.common.POETRY_TOOL_ID
import com.intellij.python.community.impl.poetry.common.icons.PythonCommunityImplPoetryCommonIcons
import javax.swing.Icon

internal class PoetryToolIdMapper : ToolIdToIconMapper {
  override val id: ToolId = POETRY_TOOL_ID
  override val icon: Icon = PythonCommunityImplPoetryCommonIcons.Poetry
  override val clazz: Class<*> = PythonCommunityImplPoetryCommonIcons::class.java
}