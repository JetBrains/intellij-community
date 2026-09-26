package com.intellij.python.hatch.impl

import com.intellij.python.community.common.tools.ToolId
import com.intellij.python.community.common.tools.spi.ToolIdToIconMapper
import com.intellij.python.hatch.common.icons.PythonHatchCommonIcons
import javax.swing.Icon

internal class HatchIdMapper : ToolIdToIconMapper {
  override val id: ToolId = HATCH_TOOL_ID
  override val icon: Icon = PythonHatchCommonIcons.Logo

  override val clazz: Class<*> = PythonHatchCommonIcons::class.java
}