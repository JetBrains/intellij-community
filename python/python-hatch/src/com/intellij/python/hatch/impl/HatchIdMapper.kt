package com.intellij.python.hatch.impl

import com.intellij.python.common.tools.ToolId
import com.intellij.python.common.tools.spi.ToolIdToIconMapper
import com.intellij.python.hatch.icons.PythonHatchIcons
import javax.swing.Icon

internal class HatchIdMapper : ToolIdToIconMapper {
  override val id: ToolId = HATCH_TOOL_ID
  override val icon: Icon = PythonHatchIcons.Logo

  override val clazz: Class<*> = PythonHatchIcons::class.java
}