// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.PathUtil
import com.intellij.util.SystemProperties
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.plugins.terminal.TerminalUtil
import java.util.concurrent.CopyOnWriteArrayList

class TerminalPromptModel(private val session: BlockTerminalSession) {
  private val listeners: MutableList<TerminalPromptStateListener> = CopyOnWriteArrayList()

  var renderingInfo: PromptRenderingInfo = PromptRenderingInfo("", emptyList())
    @RequiresEdt
    get
    private set

  init {
    session.addCommandListener(object : ShellCommandListener {
      override fun promptStateUpdated(newState: TerminalPromptState) {
        val updatedInfo = calculateRenderingInfo(newState)
        runInEdt {
          renderingInfo = updatedInfo
          listeners.forEach { it.promptStateUpdated(updatedInfo) }
        }
      }
    })
  }

  private fun calculateRenderingInfo(state: TerminalPromptState): PromptRenderingInfo {
    val components = getPromptComponents(state)
    val builder = StringBuilder()
    val highlightings = mutableListOf<HighlightingInfo>()
    for (component in components) {
      val startOffset = builder.length
      builder.append(component.text)
      highlightings.add(HighlightingInfo(startOffset, builder.length, component.attributesProvider))
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

  fun addListener(listener: TerminalPromptStateListener, disposable: Disposable) {
    TerminalUtil.addItem(listeners, listener, disposable)
  }

  private class PromptComponentInfo(val text: String, val attributesProvider: TextAttributesProvider)
}

interface TerminalPromptStateListener {
  fun promptStateUpdated(renderingInfo: PromptRenderingInfo)
}

data class PromptRenderingInfo(val text: @NlsSafe String, val highlightings: List<HighlightingInfo>)

