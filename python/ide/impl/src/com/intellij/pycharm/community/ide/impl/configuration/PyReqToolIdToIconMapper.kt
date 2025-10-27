// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.configuration

import com.intellij.python.common.tools.ToolId
import com.intellij.python.common.tools.spi.ToolIdToIconMapper
import com.jetbrains.python.icons.PythonIcons
import javax.swing.Icon

internal class PyReqToolIdToIconMapper  : ToolIdToIconMapper {
  override val id: ToolId = PY_REQ_TOOL_ID
  override val icon: Icon = PythonIcons.Python.Virtualenv
  override val clazz: Class<*> = PythonIcons::class.java
}