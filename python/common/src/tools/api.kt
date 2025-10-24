package com.intellij.python.common.tools

import com.intellij.python.common.tools.spi.ToolIdToIconMapper
import javax.swing.Icon


/**
 * Get icon for certain tool id.
 * This icon comes along with class because jewel needs it
 */
fun getIcon(id: ToolId): Pair<Icon, Class<*>>? = ToolIdToIconMapper.EP.extensionList.firstOrNull { it.id == id }?.let { Pair(it.icon, it.clazz) }