// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.PathUtil
import com.intellij.util.SystemProperties

internal class BuiltInPromptRenderer(private val session: BlockTerminalSession) : TerminalPromptRenderer {
  override fun calculateRenderingInfo(state: TerminalPromptState): PromptRenderingInfo {
    val components = getPromptComponents(state)
    val builder = StringBuilder()
    val highlightings = mutableListOf<HighlightingInfo>()
    for (component in components) {
      val startOffset = builder.length
      builder.append(component.text)
      highlightings.add(HighlightingInfo(startOffset, builder.length, component.attributes))
    }
    return PromptRenderingInfo(builder.toString(), highlightings)
  }

  private fun getPromptComponents(state: TerminalPromptState): List<PromptComponentInfo> {
    val result = mutableListOf<PromptComponentInfo>()
    val greenAttributes = plainAttributes(TerminalUiUtils.GREEN_COLOR_INDEX)
    val yellowAttributes = plainAttributes(TerminalUiUtils.YELLOW_COLOR_INDEX)
    val defaultAttributes = EmptyTextAttributesProvider

    fun addComponent(text: String, attributesProvider: TextAttributesProvider) {
      result.add(PromptComponentInfo(text, attributesProvider))
    }

    if (!state.virtualEnv.isNullOrBlank()) {
      val venvName = PathUtil.getFileName(state.virtualEnv)
      addComponent("($venvName)", greenAttributes)
    }
    if (!state.condaEnv.isNullOrBlank()) {
      addComponent("(${state.condaEnv})", greenAttributes)
    }
    if (result.isNotEmpty()) {
      addComponent(" ", defaultAttributes)
    }
    addComponent(calculateDirectoryText(state.currentDirectory), defaultAttributes)
    if (!state.gitBranch.isNullOrBlank()) {
      addComponent(" ", defaultAttributes)
      addComponent("git:", yellowAttributes)
      addComponent("[${state.gitBranch}]", greenAttributes)
    }
    addComponent("\n", defaultAttributes)
    return result
  }

  private fun calculateDirectoryText(directory: String): @NlsSafe String {
    return if (directory != SystemProperties.getUserHome()) {
      FileUtil.getLocationRelativeToUserHome(directory, false)
    }
    else "~"
  }

  private fun plainAttributes(colorIndex: Int): TextAttributesProvider {
    return TerminalUiUtils.plainAttributesProvider(colorIndex, session.colorPalette)
  }

  private class PromptComponentInfo(val text: String, val attributes: TextAttributesProvider)
}