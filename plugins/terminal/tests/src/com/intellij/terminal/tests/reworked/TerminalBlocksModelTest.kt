// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.reworked

import com.intellij.openapi.application.EDT
import com.intellij.terminal.session.TerminalBlocksModelState
import com.intellij.terminal.session.TerminalOutputBlock
import com.intellij.terminal.tests.reworked.util.TerminalTestUtil
import com.intellij.terminal.tests.reworked.util.TerminalTestUtil.update
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.jetbrains.plugins.terminal.block.reworked.TerminalBlocksModelImpl
import org.jetbrains.plugins.terminal.block.reworked.TerminalOutputModel
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
internal class TerminalBlocksModelTest : BasePlatformTestCase() {
  override fun runInDispatchThread(): Boolean = false

  @Test
  fun `initial block is replaced with a new one`() = runBlocking(Dispatchers.EDT) {
    val outputModel = TerminalTestUtil.createOutputModel()
    val blocksModel = TerminalBlocksModelImpl(outputModel.document)

    outputModel.update(0, "\n\n\n")

    assertEquals(1, blocksModel.blocks.size)

    blocksModel.promptStarted(0)
    outputModel.update(0, "myPrompt: \n\n\n")
    blocksModel.promptFinished(10)

    assertEquals(1, blocksModel.blocks.size)
  }

  @Test
  fun `command start offset is set correctly after prompt finish`() = runBlocking(Dispatchers.EDT) {
    val outputModel = TerminalTestUtil.createOutputModel()
    val blocksModel = TerminalBlocksModelImpl(outputModel.document)

    outputModel.update(0, "\n\n\n")
    blocksModel.promptStarted(0)
    outputModel.update(0, "myPrompt: \n\n\n")
    blocksModel.promptFinished(10)

    val block = blocksModel.blocks.singleOrNull() ?: error("Single block expected")

    assertNotEquals(-1, block.commandStartOffset)
    assertEquals("myPrompt: ", outputModel.getText(block.startOffset, block.commandStartOffset))
    assertEquals(-1, block.outputStartOffset)
    assertEquals("myPrompt: \n\n\n", outputModel.getText(block.startOffset, block.endOffset))
  }

  @Test
  fun `block end offset is updated on command typing`() = runBlocking(Dispatchers.EDT) {
    val outputModel = TerminalTestUtil.createOutputModel()
    val blocksModel = TerminalBlocksModelImpl(outputModel.document)

    outputModel.update(0, "\n\n\n")
    blocksModel.promptStarted(0)
    outputModel.update(0, "myPrompt: \n\n\n")
    blocksModel.promptFinished(10)
    outputModel.update(0, "myPrompt: myCommand\n\n\n")

    val block = blocksModel.blocks.singleOrNull() ?: error("Single block expected")

    assertEquals("myPrompt: myCommand\n\n\n", outputModel.getText(block.startOffset, block.endOffset))
  }

  @Test
  fun `output start offset is set correctly after command start`() = runBlocking(Dispatchers.EDT) {
    val outputModel = TerminalTestUtil.createOutputModel()
    val blocksModel = TerminalBlocksModelImpl(outputModel.document)

    outputModel.update(0, "\n\n\n")
    blocksModel.promptStarted(0)
    outputModel.update(0, "myPrompt: \n\n\n")
    blocksModel.promptFinished(10)
    outputModel.update(0, "myPrompt: myCommand\n\n\n")
    blocksModel.commandStarted(20)
    outputModel.update(1, "someOutput\n\n")

    val block = blocksModel.blocks.singleOrNull() ?: error("Single block expected")
    assertEquals("myCommand\n", outputModel.getText(block.commandStartOffset, block.outputStartOffset))
    assertEquals("someOutput\n\n", outputModel.getText(block.outputStartOffset, block.endOffset))
    assertEquals("myPrompt: myCommand\nsomeOutput\n\n", outputModel.getText(block.startOffset, block.endOffset))
  }

  @Test
  fun `new block is created after next prompt start`() = runBlocking(Dispatchers.EDT) {
    val outputModel = TerminalTestUtil.createOutputModel()
    val blocksModel = TerminalBlocksModelImpl(outputModel.document)

    outputModel.update(0, "\n\n\n")
    blocksModel.promptStarted(0)
    outputModel.update(0, "myPrompt: \n\n\n")
    blocksModel.promptFinished(10)
    outputModel.update(0, "myPrompt: myCommand\n\n\n")
    blocksModel.commandStarted(20)
    outputModel.update(1, "someOutput\n\n")
    blocksModel.promptStarted(31)
    outputModel.update(2, "updatedPrompt: \n")
    blocksModel.promptFinished(46)

    assertEquals(2, blocksModel.blocks.size)

    val firstBlock = blocksModel.blocks[0]
    assertEquals("myPrompt: myCommand\nsomeOutput\n", outputModel.getText(firstBlock.startOffset, firstBlock.endOffset))
    val secondBlock = blocksModel.blocks[1]
    assertEquals("updatedPrompt: \n", outputModel.getText(secondBlock.startOffset, secondBlock.endOffset))
  }

  @Test
  fun `initial block is left if there was some text`() = runBlocking(Dispatchers.EDT) {
    val outputModel = TerminalTestUtil.createOutputModel()
    val blocksModel = TerminalBlocksModelImpl(outputModel.document)

    outputModel.update(0, "\n\n\n")
    outputModel.update(0, "welcomeText\n\n\n")
    blocksModel.promptStarted(12)
    outputModel.update(1, "myPrompt: \n\n")
    blocksModel.promptFinished(22)

    assertEquals(2, blocksModel.blocks.size)

    val firstBlock = blocksModel.blocks[0]
    assertEquals(-1, firstBlock.commandStartOffset)
    assertEquals(-1, firstBlock.outputStartOffset)
    assertEquals("welcomeText\n", outputModel.getText(firstBlock.startOffset, firstBlock.endOffset))

    val secondBlock = blocksModel.blocks[1]
    assertEquals("myPrompt: \n\n", outputModel.getText(secondBlock.startOffset, secondBlock.endOffset))
  }

  @Test
  fun `blocks are preserved after full text replace`() = runBlocking(Dispatchers.EDT) {
    val outputModel = TerminalTestUtil.createOutputModel()
    val blocksModel = TerminalBlocksModelImpl(outputModel.document)

    // Prepare
    outputModel.update(0, "\n\n\n")
    blocksModel.promptStarted(0)
    outputModel.update(0, "myPrompt: \n\n\n")
    blocksModel.promptFinished(10)
    outputModel.update(0, "myPrompt: myCommand\n\n\n")
    blocksModel.commandStarted(20)
    outputModel.update(1, "output123\n\n")
    blocksModel.promptStarted(30)
    outputModel.update(2, "myPrompt: \n")
    blocksModel.promptFinished(40)
    outputModel.update(2, "myPrompt: otherCommand\n")

    assertEquals(2, blocksModel.blocks.size)

    // Test
    outputModel.update(0, """
      myPrompt: myCommand
      output123
      myPrompt: otherModifiedCommand
      
    """.trimIndent())

    assertEquals(2, blocksModel.blocks.size)

    val firstBlock = blocksModel.blocks[0]
    assertEquals("myPrompt: myCommand\noutput123\n", outputModel.getText(firstBlock.startOffset, firstBlock.endOffset))
    val secondBlock = blocksModel.blocks[1]
    assertEquals("myPrompt: otherModifiedCommand\n", outputModel.getText(secondBlock.startOffset, secondBlock.endOffset))
  }

  @Test
  fun `single block is left after clear`() = runBlocking(Dispatchers.EDT) {
    val outputModel = TerminalTestUtil.createOutputModel()
    val blocksModel = TerminalBlocksModelImpl(outputModel.document)

    // Prepare
    outputModel.update(0, "\n\n\n")
    blocksModel.promptStarted(0)
    outputModel.update(0, "myPrompt: \n\n\n")
    blocksModel.promptFinished(10)
    outputModel.update(0, "myPrompt: myCommand\n\n\n")
    blocksModel.commandStarted(20)
    outputModel.update(1, "output123\n\n")
    blocksModel.promptStarted(30)
    outputModel.update(2, "myPrompt: \n")
    blocksModel.promptFinished(40)

    assertEquals(2, blocksModel.blocks.size)

    // Test
    outputModel.update(2, "myPrompt: clear\n")
    blocksModel.commandStarted(46)
    outputModel.update(0, "\n\n\n")  // all text cleared
    blocksModel.promptStarted(0)
    outputModel.update(0, "myPrompt: \n\n\n")
    blocksModel.promptFinished(10)

    val block = blocksModel.blocks.singleOrNull() ?: error("Single block expected")
    assertEquals("myPrompt: \n\n\n", outputModel.getText(block.startOffset, block.endOffset))
  }

  /**
   * I'm not sure that this case is possible, because usually after clear-related things, an empty screen with new lines is left.
   * But let's test this case too, to ensure that model can handle it.
   */
  @Test
  fun `single block is left after all text removed`() = runBlocking(Dispatchers.EDT) {
    val outputModel = TerminalTestUtil.createOutputModel()
    val blocksModel = TerminalBlocksModelImpl(outputModel.document)

    // Prepare
    outputModel.update(0, "\n\n\n")
    blocksModel.promptStarted(0)
    outputModel.update(0, "myPrompt: \n\n\n")
    blocksModel.promptFinished(10)
    outputModel.update(0, "myPrompt: myCommand\n\n\n")
    blocksModel.commandStarted(20)
    outputModel.update(1, "output123\n\n")
    blocksModel.promptStarted(30)
    outputModel.update(2, "myPrompt: \n")
    blocksModel.promptFinished(40)

    assertEquals(2, blocksModel.blocks.size)

    // Test
    outputModel.update(0, "")

    val block = blocksModel.blocks.singleOrNull() ?: error("Single block expected")
    assertEquals("", outputModel.getText(block.startOffset, block.endOffset))
  }

  @Test
  fun `blocks positions are adjusted after output start trimmed`() = runBlocking(Dispatchers.EDT) {
    val outputModel = TerminalTestUtil.createOutputModel(maxLength = 30)
    val blocksModel = TerminalBlocksModelImpl(outputModel.document)

    outputModel.update(0, "\n\n\n")
    blocksModel.promptStarted(0)
    outputModel.update(0, "myPrompt: \n\n\n")
    blocksModel.promptFinished(10)
    outputModel.update(0, "myPrompt: myCommand\n\n\n")
    blocksModel.commandStarted(20)
    outputModel.update(1, "output123456\n\n")  // 4 chars from the start should be trimmed
    blocksModel.promptStarted(29)
    outputModel.update(2, "myPrompt: \n")      // 10 chars from the start should be trimmed
    blocksModel.promptFinished(29)

    assertEquals(2, blocksModel.blocks.size)

    val firstBlock = blocksModel.blocks[0]
    assertEquals("", outputModel.getText(firstBlock.startOffset, firstBlock.commandStartOffset))
    assertEquals("mmand\n", outputModel.getText(firstBlock.commandStartOffset, firstBlock.outputStartOffset))
    assertEquals("mmand\noutput123456\n", outputModel.getText(firstBlock.startOffset, firstBlock.endOffset))

    val secondBlock = blocksModel.blocks[1]
    assertEquals("myPrompt: \n", outputModel.getText(secondBlock.startOffset, secondBlock.endOffset))
  }

  @Test
  fun `blocks was removed after output start trimmed`() = runBlocking(Dispatchers.EDT) {
    val outputModel = TerminalTestUtil.createOutputModel(maxLength = 30)
    val blocksModel = TerminalBlocksModelImpl(outputModel.document)

    outputModel.update(0, "\n\n\n")
    outputModel.update(0, "welcome12\n\n\n")
    blocksModel.promptStarted(10)
    outputModel.update(1, "myPrompt: \n\n")
    blocksModel.promptFinished(20)
    outputModel.update(1, "myPrompt: myCommand\n\n")  // 1 char from that start should be trimmed
    blocksModel.commandStarted(29)
    outputModel.update(2, "output123456\n")           // 12 chars from the start should be trimmed (and first block removed)
    blocksModel.promptStarted(30)
    outputModel.update(3, "myPrompt: \n")             // 10 chars from the start should be trimmed
    blocksModel.promptFinished(29)

    assertEquals(2, blocksModel.blocks.size)

    val firstBlock = blocksModel.blocks[0]
    assertEquals("", outputModel.getText(firstBlock.startOffset, firstBlock.commandStartOffset))
    assertEquals("mmand\n", outputModel.getText(firstBlock.commandStartOffset, firstBlock.outputStartOffset))
    assertEquals("mmand\noutput123456\n", outputModel.getText(firstBlock.startOffset, firstBlock.endOffset))

    val secondBlock = blocksModel.blocks[1]
    assertEquals("myPrompt: \n", outputModel.getText(secondBlock.startOffset, secondBlock.endOffset))
  }

  @Test
  fun `check state is dumped correctly`() = runBlocking(Dispatchers.EDT) {
    val outputModel = TerminalTestUtil.createOutputModel()
    val blocksModel = TerminalBlocksModelImpl(outputModel.document)

    // Prepare
    outputModel.update(0, "\n\n\n")
    blocksModel.promptStarted(0)
    outputModel.update(0, "myPrompt: \n\n\n")
    blocksModel.promptFinished(10)
    outputModel.update(0, "myPrompt: myCommand\n\n\n")
    blocksModel.commandStarted(20)
    outputModel.update(1, "someOutput\n\n")
    blocksModel.promptStarted(31)
    outputModel.update(2, "updatedPrompt: \n")
    blocksModel.promptFinished(46)

    // Test
    val state = blocksModel.dumpState()

    assertEquals(3, state.blockIdCounter)
    assertEquals(2, state.blocks.size)

    val expectedFirstBlock = TerminalOutputBlock(
      id = 1,
      startOffset = 0,
      commandStartOffset = 10,
      outputStartOffset = 20,
      endOffset = 31,
      exitCode = null
    )
    assertEquals(expectedFirstBlock, state.blocks[0])

    val expectedSecondBlock = TerminalOutputBlock(
      id = 2,
      startOffset = 31,
      commandStartOffset = 46,
      outputStartOffset = -1,
      endOffset = 47,
      exitCode = null
    )
    assertEquals(expectedSecondBlock, state.blocks[1])
  }

  @Test
  fun `check state is restored correctly`() = runBlocking(Dispatchers.EDT) {
    val outputModel = TerminalTestUtil.createOutputModel()
    val blocksModel = TerminalBlocksModelImpl(outputModel.document)

    val firstBlock = TerminalOutputBlock(
      id = 1,
      startOffset = 0,
      commandStartOffset = 10,
      outputStartOffset = 20,
      endOffset = 31,
      exitCode = null
    )
    val secondBlock = TerminalOutputBlock(
      id = 2,
      startOffset = 31,
      commandStartOffset = 46,
      outputStartOffset = -1,
      endOffset = 47,
      exitCode = null
    )
    val state = TerminalBlocksModelState(
      blocks = listOf(firstBlock, secondBlock),
      blockIdCounter = 3
    )

    blocksModel.restoreFromState(state)

    assertEquals(3, blocksModel.blockIdCounter)
    val blocks = blocksModel.blocks
    assertEquals(2, blocks.size)
    assertEquals(firstBlock, blocks[0])
    assertEquals(secondBlock, blocks[1])
  }

  @Test
  fun `check state is restored correctly after applying dumped state`() = runBlocking(Dispatchers.EDT) {
    val sourceOutputModel = TerminalTestUtil.createOutputModel()
    val sourceBlocksModel = TerminalBlocksModelImpl(sourceOutputModel.document)

    // Prepare
    sourceOutputModel.update(0, "\n\n\n")
    sourceBlocksModel.promptStarted(0)
    sourceOutputModel.update(0, "myPrompt: \n\n\n")
    sourceBlocksModel.promptFinished(10)
    sourceOutputModel.update(0, "myPrompt: myCommand\n\n\n")
    sourceBlocksModel.commandStarted(20)
    sourceOutputModel.update(1, "someOutput\n\n")
    sourceBlocksModel.promptStarted(31)
    sourceOutputModel.update(2, "updatedPrompt: \n")
    sourceBlocksModel.promptFinished(46)

    // Test
    val state = sourceBlocksModel.dumpState()
    val newOutputModel = TerminalTestUtil.createOutputModel()
    val newBlocksModel = TerminalBlocksModelImpl(newOutputModel.document)
    newBlocksModel.restoreFromState(state)

    assertEquals(3, state.blockIdCounter)
    assertEquals(2, state.blocks.size)

    val expectedFirstBlock = TerminalOutputBlock(
      id = 1,
      startOffset = 0,
      commandStartOffset = 10,
      outputStartOffset = 20,
      endOffset = 31,
      exitCode = null
    )
    assertEquals(expectedFirstBlock, state.blocks[0])

    val expectedSecondBlock = TerminalOutputBlock(
      id = 2,
      startOffset = 31,
      commandStartOffset = 46,
      outputStartOffset = -1,
      endOffset = 47,
      exitCode = null
    )
    assertEquals(expectedSecondBlock, state.blocks[1])
  }

  private fun TerminalOutputModel.getText(startOffset: Int, endOffset: Int): String {
    return document.charsSequence.substring(startOffset, endOffset)
  }
}