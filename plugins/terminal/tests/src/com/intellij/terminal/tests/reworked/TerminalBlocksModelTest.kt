// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.reworked

import com.intellij.openapi.application.EDT
import com.intellij.terminal.tests.reworked.util.TerminalTestUtil
import com.intellij.terminal.tests.reworked.util.TerminalTestUtil.update
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.jetbrains.plugins.terminal.block.reworked.*
import org.jetbrains.plugins.terminal.session.TerminalBlocksModelState
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
    val blocksModel = TerminalBlocksModelImpl(outputModel, testRootDisposable)

    outputModel.update(0, "\n\n\n")

    assertEquals(1, blocksModel.blocks.size)

    blocksModel.promptStarted(outputModel.startOffset + 0L)
    outputModel.update(0, "myPrompt: \n\n\n")
    blocksModel.promptFinished(outputModel.startOffset + 10L)

    assertEquals(1, blocksModel.blocks.size)
  }

  @Test
  fun `command start offset is set correctly after prompt finish`() = runBlocking(Dispatchers.EDT) {
    val outputModel = TerminalTestUtil.createOutputModel()
    val blocksModel = TerminalBlocksModelImpl(outputModel, testRootDisposable)

    outputModel.update(0, "\n\n\n")
    blocksModel.promptStarted(outputModel.startOffset + 0L)
    outputModel.update(0, "myPrompt: \n\n\n")
    blocksModel.promptFinished(outputModel.startOffset + 10L)

    val block = blocksModel.blocks.singleOrNull() as? TerminalCommandBlock ?: error("Single command block expected")

    assertNotEquals(null, block.commandStartOffset)
    assertEquals("myPrompt: ", outputModel.getTextAsString(block.startOffset, block.commandStartOffset!!))
    assertEquals(null, block.outputStartOffset)
    assertEquals("myPrompt: \n\n\n", outputModel.getTextAsString(block.startOffset, block.endOffset))
  }

  @Test
  fun `block end offset is updated on command typing`() = runBlocking(Dispatchers.EDT) {
    val outputModel = TerminalTestUtil.createOutputModel()
    val blocksModel = TerminalBlocksModelImpl(outputModel, testRootDisposable)

    outputModel.update(0, "\n\n\n")
    blocksModel.promptStarted(outputModel.startOffset + 0L)
    outputModel.update(0, "myPrompt: \n\n\n")
    blocksModel.promptFinished(outputModel.startOffset + 10L)
    outputModel.update(0, "myPrompt: myCommand\n\n\n")

    val block = blocksModel.blocks.singleOrNull() ?: error("Single block expected")

    assertEquals("myPrompt: myCommand\n\n\n", outputModel.getTextAsString(block.startOffset, block.endOffset))
  }

  @Test
  fun `output start offset is set correctly after command start`() = runBlocking(Dispatchers.EDT) {
    val outputModel = TerminalTestUtil.createOutputModel()
    val blocksModel = TerminalBlocksModelImpl(outputModel, testRootDisposable)

    outputModel.update(0, "\n\n\n")
    blocksModel.promptStarted(outputModel.startOffset + 0L)
    outputModel.update(0, "myPrompt: \n\n\n")
    blocksModel.promptFinished(outputModel.startOffset + 10L)
    outputModel.update(0, "myPrompt: myCommand\n\n\n")
    blocksModel.commandStarted(outputModel.startOffset + 20L)
    outputModel.update(1, "someOutput\n\n")

    val block = blocksModel.blocks.singleOrNull() as? TerminalCommandBlock ?: error("Single command block expected")
    assertEquals("myCommand\n", outputModel.getTextAsString(block.commandStartOffset!!, block.outputStartOffset!!))
    assertEquals("someOutput\n\n", outputModel.getTextAsString(block.outputStartOffset!!, block.endOffset))
    assertEquals("myPrompt: myCommand\nsomeOutput\n\n", outputModel.getTextAsString(block.startOffset, block.endOffset))
  }

  @Test
  fun `new block is created after next prompt start`() = runBlocking(Dispatchers.EDT) {
    val outputModel = TerminalTestUtil.createOutputModel()
    val blocksModel = TerminalBlocksModelImpl(outputModel, testRootDisposable)

    outputModel.update(0, "\n\n\n")
    blocksModel.promptStarted(outputModel.startOffset + 0L)
    outputModel.update(0, "myPrompt: \n\n\n")
    blocksModel.promptFinished(outputModel.startOffset + 10L)
    outputModel.update(0, "myPrompt: myCommand\n\n\n")
    blocksModel.commandStarted(outputModel.startOffset + 20L)
    outputModel.update(1, "someOutput\n\n")
    blocksModel.promptStarted(outputModel.startOffset + 31L)
    outputModel.update(2, "updatedPrompt: \n")
    blocksModel.promptFinished(outputModel.startOffset + 46L)

    assertEquals(2, blocksModel.blocks.size)

    val firstBlock = blocksModel.blocks[0]
    assertEquals("myPrompt: myCommand\nsomeOutput\n", outputModel.getTextAsString(firstBlock.startOffset, firstBlock.endOffset))
    val secondBlock = blocksModel.blocks[1]
    assertEquals("updatedPrompt: \n", outputModel.getTextAsString(secondBlock.startOffset, secondBlock.endOffset))
  }

  @Test
  fun `initial block is left if there was some text`() = runBlocking(Dispatchers.EDT) {
    val outputModel = TerminalTestUtil.createOutputModel()
    val blocksModel = TerminalBlocksModelImpl(outputModel, testRootDisposable)

    outputModel.update(0, "\n\n\n")
    outputModel.update(0, "welcomeText\n\n\n")
    blocksModel.promptStarted(outputModel.startOffset + 12L)
    outputModel.update(1, "myPrompt: \n\n")
    blocksModel.promptFinished(outputModel.startOffset + 22L)

    assertEquals(2, blocksModel.blocks.size)

    val firstBlock = blocksModel.blocks[0] as TerminalCommandBlock
    assertEquals(null, firstBlock.commandStartOffset)
    assertEquals(null, firstBlock.outputStartOffset)
    assertEquals("welcomeText\n", outputModel.getTextAsString(firstBlock.startOffset, firstBlock.endOffset))

    val secondBlock = blocksModel.blocks[1] as TerminalCommandBlock
    assertEquals("myPrompt: \n\n", outputModel.getTextAsString(secondBlock.startOffset, secondBlock.endOffset))
  }

  @Test
  fun `blocks are preserved after full text replace`() = runBlocking(Dispatchers.EDT) {
    val outputModel = TerminalTestUtil.createOutputModel()
    val blocksModel = TerminalBlocksModelImpl(outputModel, testRootDisposable)

    // Prepare
    outputModel.update(0, "\n\n\n")
    blocksModel.promptStarted(outputModel.startOffset + 0L)
    outputModel.update(0, "myPrompt: \n\n\n")
    blocksModel.promptFinished(outputModel.startOffset + 10L)
    outputModel.update(0, "myPrompt: myCommand\n\n\n")
    blocksModel.commandStarted(outputModel.startOffset + 20L)
    outputModel.update(1, "output123\n\n")
    blocksModel.promptStarted(outputModel.startOffset + 30L)
    outputModel.update(2, "myPrompt: \n")
    blocksModel.promptFinished(outputModel.startOffset + 40L)
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
    assertEquals("myPrompt: myCommand\noutput123\n", outputModel.getTextAsString(firstBlock.startOffset, firstBlock.endOffset))
    val secondBlock = blocksModel.blocks[1]
    assertEquals("myPrompt: otherModifiedCommand\n", outputModel.getTextAsString(secondBlock.startOffset, secondBlock.endOffset))
  }

  @Test
  fun `single block is left after full replace`() = runBlocking(Dispatchers.EDT) {
    val outputModel = TerminalTestUtil.createOutputModel()
    val blocksModel = TerminalBlocksModelImpl(outputModel, testRootDisposable)

    // Prepare
    outputModel.update(0, "\n\n\n")
    blocksModel.promptStarted(outputModel.startOffset + 0L)
    outputModel.update(0, "myPrompt: \n\n\n")
    blocksModel.promptFinished(outputModel.startOffset + 10L)
    outputModel.update(0, "myPrompt: myCommand\n\n\n")
    blocksModel.commandStarted(outputModel.startOffset + 20L)
    outputModel.update(1, "output123\n\n")
    blocksModel.promptStarted(outputModel.startOffset + 30L)
    outputModel.update(2, "myPrompt: \n")
    blocksModel.promptFinished(outputModel.startOffset + 40L)

    assertEquals(2, blocksModel.blocks.size)

    // Test
    outputModel.update(2, "myPrompt: clear\n")
    blocksModel.commandStarted(outputModel.startOffset + 46L)
    outputModel.update(0, "\n\n\n")  // full replace
    blocksModel.promptStarted(outputModel.startOffset + 0L)
    outputModel.update(0, "myPrompt: \n\n\n")
    blocksModel.promptFinished(outputModel.startOffset + 10L)

    val block = blocksModel.blocks.singleOrNull() ?: error("Single block expected")
    assertEquals("myPrompt: \n\n\n", outputModel.getTextAsString(block.startOffset, block.endOffset))
  }

  @Test
  fun `single block is left after clear`() = runBlocking(Dispatchers.EDT) {
    val outputModel = TerminalTestUtil.createOutputModel()
    val blocksModel = TerminalBlocksModelImpl(outputModel, testRootDisposable)

    // Prepare
    outputModel.update(0, "\n\n\n")
    blocksModel.promptStarted(outputModel.startOffset + 0L)
    outputModel.update(0, "myPrompt: \n\n\n")
    blocksModel.promptFinished(outputModel.startOffset + 10L)
    outputModel.update(0, "myPrompt: myCommand\n\n\n")
    blocksModel.commandStarted(outputModel.startOffset + 20L)
    outputModel.update(1, "output123\n\n")
    blocksModel.promptStarted(outputModel.startOffset + 30L)
    outputModel.update(2, "myPrompt: \n")
    blocksModel.promptFinished(outputModel.startOffset + 45L)
    outputModel.update(2, "myPrompt: clear\n")

    assertEquals(2, blocksModel.blocks.size)

    // Test
    outputModel.update(0, "")

    val block = blocksModel.blocks.singleOrNull() ?: error("Single block expected")
    assertEquals("", outputModel.getTextAsString(block.startOffset, block.endOffset))
  }

  @Test
  fun `single block is left after clear (with trimming)`() = runBlocking(Dispatchers.EDT) {
    val outputModel = TerminalTestUtil.createOutputModel(100)
    val blocksModel = TerminalBlocksModelImpl(outputModel, testRootDisposable)

    // Prepare
    outputModel.update(0, "\n\n\n")
    blocksModel.promptStarted(outputModel.startOffset + 0L)
    outputModel.update(0, "myPrompt: \n\n\n")
    blocksModel.promptFinished(outputModel.startOffset + 10L)
    outputModel.update(0, "myPrompt: myCommand\n\n\n")
    blocksModel.commandStarted(outputModel.startOffset + 20L)
    outputModel.update(1, "a".repeat(100) + "\n\n")
    outputModel.update(2, "output123\n")
    blocksModel.promptStarted(outputModel.startOffset + 100L)
    outputModel.update(3, "myPrompt: ")
    blocksModel.promptFinished(outputModel.startOffset + 100L)
    outputModel.update(3, "myPrompt: clear")

    assertEquals(2, blocksModel.blocks.size)

    // Test
    outputModel.update(0, "")

    val block = blocksModel.blocks.singleOrNull() ?: error("Single block expected")
    assertEquals("", outputModel.getTextAsString(block.startOffset, block.endOffset))
  }

  @Test
  fun `block positions stay the same after output start trimmed`() = runBlocking(Dispatchers.EDT) {
    val outputModel = TerminalTestUtil.createOutputModel(maxLength = 30)
    val blocksModel = TerminalBlocksModelImpl(outputModel, testRootDisposable)

    outputModel.update(0, "\n\n\n")
    blocksModel.promptStarted(TerminalOffset.ZERO)
    outputModel.update(0, "myPrompt: \n\n\n")
    blocksModel.promptFinished(TerminalOffset.of(10))
    outputModel.update(0, "myPrompt: myCommand\n\n\n")
    blocksModel.commandStarted(TerminalOffset.of(20))
    outputModel.update(1, "output123456\n\n")  // 4 chars from the start should be trimmed
    blocksModel.promptStarted(TerminalOffset.of(33))
    outputModel.update(2, "myPrompt: \n")      // 10 chars from the start should be trimmed
    blocksModel.promptFinished(TerminalOffset.of(43))

    assertEquals(2, blocksModel.blocks.size)

    val firstBlock = blocksModel.blocks[0] as TerminalCommandBlock
    assertEquals(TerminalOffset.ZERO, firstBlock.startOffset)
    assertEquals(TerminalOffset.of(10), firstBlock.commandStartOffset)
    assertEquals(TerminalOffset.of(20), firstBlock.outputStartOffset)
    assertEquals(TerminalOffset.of(33), firstBlock.endOffset)

    val secondBlock = blocksModel.blocks[1] as TerminalCommandBlock
    assertEquals(TerminalOffset.of(33), secondBlock.startOffset)
    assertEquals(TerminalOffset.of(43), secondBlock.commandStartOffset)
    assertEquals(TerminalOffset.of(44), secondBlock.endOffset)
  }

  @Test
  fun `block was removed after output start trimmed`() = runBlocking(Dispatchers.EDT) {
    val outputModel = TerminalTestUtil.createOutputModel(maxLength = 30)
    val blocksModel = TerminalBlocksModelImpl(outputModel, testRootDisposable)

    outputModel.update(0, "\n\n\n")
    outputModel.update(0, "welcome12\n\n\n")
    blocksModel.promptStarted(TerminalOffset.of(10))
    outputModel.update(1, "myPrompt: \n\n")
    blocksModel.promptFinished(TerminalOffset.of(20))
    outputModel.update(1, "myPrompt: myCommand\n\n")  // 1 char from the start should be trimmed
    blocksModel.commandStarted(TerminalOffset.of(30))
    outputModel.update(2, "output123456\n")           // 12 chars from the start should be trimmed (and first block removed)
    blocksModel.promptStarted(TerminalOffset.of(43))
    outputModel.update(3, "myPrompt: \n")             // 10 chars from the start should be trimmed
    blocksModel.promptFinished(TerminalOffset.of(53))

    assertEquals(2, blocksModel.blocks.size)

    val firstBlock = blocksModel.blocks[0] as TerminalCommandBlock
    assertEquals(TerminalOffset.of(10), firstBlock.startOffset)
    assertEquals(TerminalOffset.of(20), firstBlock.commandStartOffset)
    assertEquals(TerminalOffset.of(30), firstBlock.outputStartOffset)
    assertEquals(TerminalOffset.of(43), firstBlock.endOffset)

    val secondBlock = blocksModel.blocks[1] as TerminalCommandBlock
    assertEquals(TerminalOffset.of(43), secondBlock.startOffset)
    assertEquals(TerminalOffset.of(53), secondBlock.commandStartOffset)
    assertEquals(TerminalOffset.of(54), secondBlock.endOffset)
  }

  @Test
  fun `check state is dumped correctly`() = runBlocking(Dispatchers.EDT) {
    val outputModel = TerminalTestUtil.createOutputModel()
    val blocksModel = TerminalBlocksModelImpl(outputModel, testRootDisposable)

    // Prepare
    outputModel.update(0, "\n\n\n")
    blocksModel.promptStarted(outputModel.startOffset + 0L)
    outputModel.update(0, "myPrompt: \n\n\n")
    blocksModel.promptFinished(outputModel.startOffset + 10L)
    outputModel.update(0, "myPrompt: myCommand\n\n\n")
    blocksModel.commandStarted(outputModel.startOffset + 20L)
    outputModel.update(1, "someOutput\n\n")
    blocksModel.promptStarted(outputModel.startOffset + 31L)
    outputModel.update(2, "updatedPrompt: \n")
    blocksModel.promptFinished(outputModel.startOffset + 46L)

    // Test
    val state = blocksModel.dumpState()

    assertEquals(3, state.blockIdCounter)
    assertEquals(2, state.blocks.size)

    val expectedFirstBlock = TerminalCommandBlockImpl(
      id = TerminalBlockIdImpl(1),
      startOffset = outputModel.startOffset + 0,
      commandStartOffset = outputModel.startOffset + 10,
      outputStartOffset = outputModel.startOffset + 20,
      endOffset = outputModel.startOffset + 31,
      exitCode = null
    )
    assertEquals(expectedFirstBlock, state.blocks[0])

    val expectedSecondBlock = TerminalCommandBlockImpl(
      id = TerminalBlockIdImpl(2),
      startOffset = outputModel.startOffset + 31,
      commandStartOffset = outputModel.startOffset + 46,
      outputStartOffset = null,
      endOffset = outputModel.startOffset + 47,
      exitCode = null
    )
    assertEquals(expectedSecondBlock, state.blocks[1])
  }

  @Test
  fun `check state is restored correctly`() = runBlocking(Dispatchers.EDT) {
    val outputModel = TerminalTestUtil.createOutputModel()
    val blocksModel = TerminalBlocksModelImpl(outputModel, testRootDisposable)

    val firstBlock = TerminalCommandBlockImpl(
      id = TerminalBlockIdImpl(1),
      startOffset = outputModel.startOffset + 0,
      commandStartOffset = outputModel.startOffset + 10,
      outputStartOffset = outputModel.startOffset + 20,
      endOffset = outputModel.startOffset + 31,
      exitCode = null
    )
    val secondBlock = TerminalCommandBlockImpl(
      id = TerminalBlockIdImpl(2),
      startOffset = outputModel.startOffset + 31,
      commandStartOffset = outputModel.startOffset + 46,
      outputStartOffset = null,
      endOffset = outputModel.startOffset + 47,
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
    val sourceBlocksModel = TerminalBlocksModelImpl(sourceOutputModel, testRootDisposable)

    // Prepare
    sourceOutputModel.update(0, "\n\n\n")
    sourceBlocksModel.promptStarted(sourceOutputModel.startOffset + 0)
    sourceOutputModel.update(0, "myPrompt: \n\n\n")
    sourceBlocksModel.promptFinished(sourceOutputModel.startOffset + 10)
    sourceOutputModel.update(0, "myPrompt: myCommand\n\n\n")
    sourceBlocksModel.commandStarted(sourceOutputModel.startOffset + 20)
    sourceOutputModel.update(1, "someOutput\n\n")
    sourceBlocksModel.promptStarted(sourceOutputModel.startOffset + 31)
    sourceOutputModel.update(2, "updatedPrompt: \n")
    sourceBlocksModel.promptFinished(sourceOutputModel.startOffset + 46)

    // Test
    val state = sourceBlocksModel.dumpState()
    val newOutputModel = TerminalTestUtil.createOutputModel()
    val newBlocksModel = TerminalBlocksModelImpl(newOutputModel, testRootDisposable)
    newBlocksModel.restoreFromState(state)

    assertEquals(3, state.blockIdCounter)
    assertEquals(2, state.blocks.size)

    val expectedFirstBlock = TerminalCommandBlockImpl(
      id = TerminalBlockIdImpl(1),
      startOffset = newOutputModel.startOffset + 0,
      commandStartOffset = newOutputModel.startOffset + 10,
      outputStartOffset = newOutputModel.startOffset + 20,
      endOffset = newOutputModel.startOffset + 31,
      exitCode = null
    )
    assertEquals(expectedFirstBlock, state.blocks[0])

    val expectedSecondBlock = TerminalCommandBlockImpl(
      id = TerminalBlockIdImpl(2),
      startOffset = newOutputModel.startOffset + 31,
      commandStartOffset = newOutputModel.startOffset + 46,
      outputStartOffset = null,
      endOffset = newOutputModel.startOffset + 47,
      exitCode = null
    )
    assertEquals(expectedSecondBlock, state.blocks[1])
  }

  private fun MutableTerminalOutputModel.getTextAsString(startOffset: TerminalOffset, endOffset: TerminalOffset): String {
    return getText(startOffset, endOffset).toString()
  }
}