package com.intellij.mcpserver.toolsets.general

import com.intellij.navigation.NavigationItem
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus

/**
 * Maps a [NavigationItem] that has no PSI target to a file location.
 * The MCP symbol search consults this point when the PSI mapping finds nothing.
 */
@ApiStatus.Internal
interface McpNavigationItemMapper {
  /**
   * Returns the location of [item], or null when this mapper does not recognize the item.
   */
  fun map(item: NavigationItem): McpNavigationItemLocation?

  companion object {
    val EP_NAME: ExtensionPointName<McpNavigationItemMapper> = ExtensionPointName("com.intellij.mcpServer.navigationItemMapper")
  }
}

/**
 * A file location with an optional match range.
 * Lines and columns are 0-based. The end column is exclusive.
 */
@ApiStatus.Internal
data class McpNavigationItemLocation(
  val file: VirtualFile,
  val startLine: Int? = null,
  val startColumn: Int? = null,
  val endLine: Int? = null,
  val endColumn: Int? = null,
)
