// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp.prompt

import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.PathUtil
import com.intellij.util.SystemProperties
import org.jetbrains.plugins.terminal.exp.*

internal class BuiltInPromptRenderer(private val sessionInfo: TerminalSessionInfo) : TerminalPromptRenderer {
  override fun calculateRenderingInfo(state: TerminalPromptState): PromptRenderingInfo {
    val content: TextWithHighlightings = getPromptComponents(state).toTextWithHighlightings()
    return PromptRenderingInfo(content.text, content.highlightings)
  }

  private fun getPromptComponents(state: TerminalPromptState): List<TextWithAttributes> {
    val result = mutableListOf<TextWithAttributes>()
    val greenAttributes = plainAttributes(TerminalUiUtils.GREEN_COLOR_INDEX)
    val yellowAttributes = plainAttributes(TerminalUiUtils.YELLOW_COLOR_INDEX)
    val defaultAttributes = EmptyTextAttributesProvider

    fun addComponent(text: String, attributesProvider: TextAttributesProvider) {
      result.add(TextWithAttributes(text, attributesProvider))
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
    return TerminalUiUtils.plainAttributesProvider(colorIndex, sessionInfo.colorPalette)
  }
}