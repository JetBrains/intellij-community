// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.venv

import com.intellij.python.community.common.tools.ToolId
import com.intellij.python.community.common.tools.spi.ToolIdToIconMapper
import com.intellij.python.venv.common.icons.PythonVenvCommonIcons
import javax.swing.Icon

internal class PyReqToolIdToIconMapper  : ToolIdToIconMapper {
  override val id: ToolId = PY_REQ_TOOL_ID
  override val icon: Icon = PythonVenvCommonIcons.VirtualEnv
  override val clazz: Class<*> = PythonVenvCommonIcons::class.java
}