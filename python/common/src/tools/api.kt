package com.intellij.python.common.tools

import com.intellij.python.common.tools.spi.ToolIdToIconMapper
import javax.swing.Icon


/**
 * Get icon for certain tool id
 */
fun getIcon(id: ToolId): Icon? = ToolIdToIconMapper.EP.extensionList.firstOrNull { it.id == id }?.icon