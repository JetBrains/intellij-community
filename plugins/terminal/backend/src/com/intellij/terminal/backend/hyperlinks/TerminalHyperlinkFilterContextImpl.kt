package com.intellij.terminal.backend.hyperlinks

import com.intellij.openapi.diagnostic.logger
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.annotations.NativePath
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.path.EelPathException
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.hyperlinks.filter.TerminalHyperlinkFilterContext

@ApiStatus.Internal
class TerminalHyperlinkFilterContextImpl(
  override val eelDescriptor: EelDescriptor,
  override val userHomeDirectory: EelPath,
) : TerminalHyperlinkFilterContext {
  @Volatile
  override var currentWorkingDirectory: EelPath? = null
    private set

  fun updateCurrentDirectory(directory: @NativePath String?) {
    currentWorkingDirectory = parseDirectory(directory)
  }

  private fun parseDirectory(directory: @NativePath String?): EelPath? {
    if (directory.isNullOrBlank()) return null
    return try {
      EelPath.parse(directory, eelDescriptor)
    }
    catch (e: EelPathException) {
      logger<TerminalHyperlinkFilterContextImpl>().info("Failed to parse path: $directory, $eelDescriptor", e)
      null
    }
  }
}
