// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.block

import com.intellij.openapi.editor.markup.TextAttributes
import junit.framework.TestCase.assertEquals
import org.jetbrains.plugins.terminal.block.output.*
import org.jetbrains.plugins.terminal.block.prompt.TerminalPromptRenderingInfo
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
internal class RightPromptAndCommandLayoutTest {
  private val promptAttributes: TextAttributesProvider = TestTextAttributesProvider("prompt")
  private val rightPromptAttributes: TextAttributesProvider = TestTextAttributesProvider("right prompt")
  private val commandAttributes: TextAttributesProvider = TestTextAttributesProvider("command")
  private val emptyAttributes: TextAttributesProvider = EmptyTextAttributesProvider

  @Test
  fun `one line prompt, one line command, right prompt is fit`() {
    val commandAndRightPrompt = createCommandAndRightPrompt(command = "command",
                                                            prompt = "left ",
                                                            rightPrompt = "right",
                                                            width = 20)
    assertEquals("command   right", commandAndRightPrompt.text)
    assertEquals(listOf(HighlightingInfo(0, 7, commandAttributes),
                        HighlightingInfo(7, 10, emptyAttributes),
                        HighlightingInfo(10, 15, rightPromptAttributes)),
                 commandAndRightPrompt.highlightings)
  }

  @Test
  fun `one line prompt, one line command, right prompt is not fit`() {
    val command = "command"
    val commandAndRightPrompt = createCommandAndRightPrompt(command = command,
                                                            prompt = "left ",
                                                            rightPrompt = "right",
                                                            width = 15)
    assertEquals(command, commandAndRightPrompt.text)
    assertEquals(listOf(HighlightingInfo(0, command.length, commandAttributes)),
                 commandAndRightPrompt.highlightings)
  }

  @Test
  fun `one line prompt, two line command, right prompt is fit`() {
    val commandAndRightPrompt = createCommandAndRightPrompt(command = "command\ncontinues",
                                                            prompt = "left ",
                                                            rightPrompt = "right",
                                                            width = 20)
    assertEquals("command   right\ncontinues", commandAndRightPrompt.text)
    assertEquals(listOf(HighlightingInfo(0, 7, commandAttributes),
                        HighlightingInfo(7, 10, emptyAttributes),
                        HighlightingInfo(10, 15, rightPromptAttributes),
                        HighlightingInfo(15, 25, commandAttributes)),
                 commandAndRightPrompt.highlightings)
  }

  @Test
  fun `one line prompt, two line command, right prompt is not fit`() {
    val command = "command\ncontinues"
    val commandAndRightPrompt = createCommandAndRightPrompt(command = command,
                                                            prompt = "left ",
                                                            rightPrompt = "right",
                                                            width = 15)
    assertEquals(command, commandAndRightPrompt.text)
    assertEquals(listOf(HighlightingInfo(0, command.length, commandAttributes)),
                 commandAndRightPrompt.highlightings)
  }

  @Test
  fun `two line prompt, one line command, right prompt is fit`() {
    val commandAndRightPrompt = createCommandAndRightPrompt(command = "command",
                                                            prompt = "first line\nleft ",
                                                            rightPrompt = "right",
                                                            width = 20)
    assertEquals("command   right", commandAndRightPrompt.text)
    assertEquals(listOf(HighlightingInfo(0, 7, commandAttributes),
                        HighlightingInfo(7, 10, emptyAttributes),
                        HighlightingInfo(10, 15, rightPromptAttributes)),
                 commandAndRightPrompt.highlightings)
  }

  @Test
  fun `two line prompt, two line command, right prompt is fit`() {
    val commandAndRightPrompt = createCommandAndRightPrompt(command = "command\ncontinues",
                                                            prompt = "first line\nleft ",
                                                            rightPrompt = "right",
                                                            width = 20)
    assertEquals("command   right\ncontinues", commandAndRightPrompt.text)
    assertEquals(listOf(HighlightingInfo(0, 7, commandAttributes),
                        HighlightingInfo(7, 10, emptyAttributes),
                        HighlightingInfo(10, 15, rightPromptAttributes),
                        HighlightingInfo(15, 25, commandAttributes)),
                 commandAndRightPrompt.highlightings)
  }

  @Test
  fun `one line prompt, empty command, right prompt is fit`() {
    val commandAndRightPrompt = createCommandAndRightPrompt(command = "",
                                                            prompt = "left ",
                                                            rightPrompt = "right",
                                                            width = 20)
    assertEquals("          right", commandAndRightPrompt.text)
    assertEquals(listOf(HighlightingInfo(0, 10, emptyAttributes),
                        HighlightingInfo(10, 15, rightPromptAttributes)),
                 commandAndRightPrompt.highlightings)
  }

  @Test
  fun `empty prompt, one line command, right prompt is fit`() {
    val commandAndRightPrompt = createCommandAndRightPrompt(command = "command",
                                                            prompt = "",
                                                            rightPrompt = "right",
                                                            width = 20)
    assertEquals("command        right", commandAndRightPrompt.text)
    assertEquals(listOf(HighlightingInfo(0, 7, commandAttributes),
                        HighlightingInfo(7, 15, emptyAttributes),
                        HighlightingInfo(15, 20, rightPromptAttributes)),
                 commandAndRightPrompt.highlightings)
  }

  @Test
  fun `one line prompt, one line command, empty right prompt`() {
    val command = "command"
    val commandAndRightPrompt = createCommandAndRightPrompt(command = command,
                                                            prompt = "left ",
                                                            rightPrompt = "",
                                                            width = 20)
    assertEquals(command, commandAndRightPrompt.text)
    assertEquals(listOf(HighlightingInfo(0, command.length, commandAttributes)),
                 commandAndRightPrompt.highlightings)
  }

  @Test
  fun `empty prompt, empty command, empty right prompt`() {
    val commandAndRightPrompt = createCommandAndRightPrompt(command = "",
                                                            prompt = "",
                                                            rightPrompt = "",
                                                            width = 20)
    assertEquals("", commandAndRightPrompt.text)
    assertEquals(emptyList<HighlightingInfo>(), commandAndRightPrompt.highlightings)
  }

  private fun createCommandAndRightPrompt(command: String, prompt: String, rightPrompt: String, width: Int): TextWithHighlightings {
    val promptInfo = TerminalPromptRenderingInfo(prompt, listOf(HighlightingInfo(0, prompt.length, promptAttributes)),
                                                 rightPrompt, listOf(HighlightingInfo(0, rightPrompt.length, rightPromptAttributes)))
    return TerminalOutputModelImpl.createCommandAndRightPromptText(command, promptInfo, commandAttributes, width)
  }

  private class TestTextAttributesProvider(val name: String) : TextAttributesProvider {
    override fun getTextAttributes(): TextAttributes = TextAttributes()

    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (javaClass != other?.javaClass) return false

      other as TestTextAttributesProvider

      return name == other.name
    }

    override fun hashCode(): Int = name.hashCode()

    override fun toString(): String = name
  }
}
