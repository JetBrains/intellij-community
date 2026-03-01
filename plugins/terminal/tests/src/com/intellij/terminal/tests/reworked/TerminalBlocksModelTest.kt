// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.reworked

import com.intellij.openapi.application.EDT
import com.intellij.terminal.tests.reworked.util.TerminalTestUtil
import com.intellij.terminal.tests.reworked.util.TerminalTestUtil.update
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.jetbrains.plugins.terminal.block.reworked.TerminalSessionModelImpl
import org.jetbrains.plugins.terminal.session.impl.TerminalBlocksModelState
import org.jetbrains.plugins.terminal.view.TerminalOffset
import org.jetbrains.plugins.terminal.view.TerminalOutputModel
import org.jetbrains.plugins.terminal.view.impl.MutableTerminalOutputModel
import org.jetbrains.plugins.terminal.view.shellIntegration.TerminalBlockIdImpl
import org.jetbrains.plugins.terminal.view.shellIntegration.TerminalCommandBlock
import org.jetbrains.plugins.terminal.view.shellIntegration.getOutputText
import org.jetbrains.plugins.terminal.view.shellIntegration.getTypedCommandText
import org.jetbrains.plugins.terminal.view.shellIntegration.impl.TerminalBlocksModelImpl
import org.jetbrains.plugins.terminal.view.shellIntegration.impl.TerminalCommandBlockImpl
import org.jetbrains.plugins.terminal.view.shellIntegration.wasExecuted
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
internal class TerminalBlocksModelTest : BasePlatformTestCase() {
  override fun runInDispatchThread(): Boolean = false

  @Test
  fun `initial block is created from empty output model`() = runBlocking(Dispatchers.EDT) {
    val outputModel = TerminalTestUtil.createOutputModel()
    val blocksModel = createBlocksModel(outputModel)

    assertEquals(1, blocksModel.blocks.size)
    val block = blocksModel.activeBlock as TerminalCommandBlock

    assertEquals(TerminalBlockIdImpl(0), block.id)
    assertEquals(TerminalOffset.ZERO, block.startOffset)
    assertEquals(TerminalOffset.ZERO, block.endOffset)
    assertEquals(null, block.commandStartOffset)
    assertEquals(null, block.outputStartOffset)
    assertEquals(null, block.exitCode)
    assertEquals(null, block.getTypedCommandText(outputModel))
    assertEquals(null, block.getOutputText(outputModel))
    assertEquals(false, block.wasExecuted)
  }

  @Test
  fun `initial block is created from non-empty output model`() = runBlocking(Dispatchers.EDT) {
    val outputModel = TerminalTestUtil.createOutputModel()
    outputModel.update(0, "some welcome text\n")

    val blocksModel = createBlocksModel(outputModel)

    assertEquals(1, blocksModel.blocks.size)
    val block = blocksModel.activeBlock as TerminalCommandBlock

    assertEquals(TerminalBlockIdImpl(0), block.id)
    assertEquals(TerminalOffset.ZERO, block.startOffset)
    assertEquals(TerminalOffset.of(18), block.endOffset)
    assertEquals(null, block.commandStartOffset)
    assertEquals(null, block.outputStartOffset)
    assertEquals(null, block.exitCode)
    assertEquals(null, block.getTypedCommandText(outputModel))
    assertEquals(null, block.getOutputText(outputModel))
    assertEquals(false, block.wasExecuted)
  }

  @Test
  fun `initial block is replaced with a new one`() = runBlocking(Dispatchers.EDT) {
    val outputModel = TerminalTestUtil.createOutputModel()
    val blocksModel = createBlocksModel(outputModel)

    outputModel.update(0, "\n\n\n")

    assertEquals(1, blocksModel.blocks.size)

    blocksModel.startNewBlock(outputModel.startOffset + 0L)
    outputModel.update(0, "myPrompt: \n\n\n")
    blocksModel.updateCommandStartOffset(outputModel.startOffset + 10L)

    assertEquals(1, blocksModel.blocks.size)
  }

  @Test
  fun `command start offset is set correctly after prompt finish`() = runBlocking(Dispatchers.EDT) {
    val outputModel = TerminalTestUtil.createOutputModel()
    val blocksModel = createBlocksModel(outputModel)

    outputModel.update(0, "\n\n\n")
    blocksModel.startNewBlock(outputModel.startOffset + 0L)
    outputModel.update(0, "myPrompt: \n\n\n")
    blocksModel.updateCommandStartOffset(outputModel.startOffset + 10L)

    assertEquals(1, blocksModel.blocks.size)
    val block = blocksModel.activeBlock as TerminalCommandBlock

    assertNotEquals(null, block.commandStartOffset)
    assertEquals("myPrompt: ", outputModel.getTextAsString(block.startOffset, block.commandStartOffset!!))
    assertEquals(null, block.outputStartOffset)
    assertEquals("myPrompt: \n\n\n", outputModel.getTextAsString(block.startOffset, block.endOffset))
  }

  @Test
  fun `command start offset is updated correctly after prompt reprinting`() = runBlocking(Dispatchers.EDT) {
    val outputModel = TerminalTestUtil.createOutputModel()
    val blocksModel = createBlocksModel(outputModel)

    // Prepare
    outputModel.update(0, "\n\n\n")
    blocksModel.startNewBlock(TerminalOffset.ZERO)
    outputModel.update(0, "myPrompt ... > \n\n\n")
    blocksModel.updateCommandStartOffset(TerminalOffset.of(15))
    outputModel.update(0, "myPrompt ... > 123\n\n\n")

    // Test
    blocksModel.startNewBlock(TerminalOffset.ZERO)
    outputModel.update(0, "myPrompt (main) > 123\n\n\n")
    blocksModel.updateCommandStartOffset(TerminalOffset.of(18))


    assertEquals(1, blocksModel.blocks.size)
    val block = blocksModel.activeBlock as TerminalCommandBlock

    assertEquals(TerminalOffset.ZERO, block.startOffset)
    assertEquals(TerminalOffset.of(18), block.commandStartOffset)
    assertEquals(null, block.outputStartOffset)
    assertEquals(TerminalOffset.of(24), block.endOffset)
    assertEquals("123", block.getTypedCommandText(outputModel))
  }

  @Test
  fun `command start offset is updated text change inside prompt`() = runBlocking(Dispatchers.EDT) {
    val outputModel = TerminalTestUtil.createOutputModel()
    val blocksModel = createBlocksModel(outputModel)

    // Prepare
    outputModel.update(0, "\n\n\n")
    blocksModel.startNewBlock(TerminalOffset.ZERO)
    outputModel.update(0, "myPrompt ... > \n\n\n")
    blocksModel.updateCommandStartOffset(TerminalOffset.of(15))
    outputModel.update(0, "myPrompt ... > 123\n\n\n")

    // Test
    outputModel.update(0, "myPrompt (main) > 123\n\n\n")

    assertEquals(1, blocksModel.blocks.size)
    val block = blocksModel.activeBlock as TerminalCommandBlock

    assertEquals(TerminalOffset.ZERO, block.startOffset)
    assertEquals(TerminalOffset.of(18), block.commandStartOffset)
    assertEquals(null, block.outputStartOffset)
    assertEquals(TerminalOffset.of(24), block.endOffset)
    assertEquals("123", block.getTypedCommandText(outputModel))
  }

  @Test
  fun `block end offset is updated on command typing`() = runBlocking(Dispatchers.EDT) {
    val outputModel = TerminalTestUtil.createOutputModel()
    val blocksModel = createBlocksModel(outputModel)

    outputModel.update(0, "\n\n\n")
    blocksModel.startNewBlock(outputModel.startOffset + 0L)
    outputModel.update(0, "myPrompt: \n\n\n")
    blocksModel.updateCommandStartOffset(outputModel.startOffset + 10L)
    outputModel.update(0, "myPrompt: myCommand\n\n\n")

    assertEquals(1, blocksModel.blocks.size)
    val block = blocksModel.activeBlock
    assertEquals("myPrompt: myCommand\n\n\n", outputModel.getTextAsString(block.startOffset, block.endOffset))
  }

  @Test
  fun `output start offset is set correctly after command start`() = runBlocking(Dispatchers.EDT) {
    val outputModel = TerminalTestUtil.createOutputModel()
    val blocksModel = createBlocksModel(outputModel)

    outputModel.update(0, "\n\n\n")
    blocksModel.startNewBlock(outputModel.startOffset + 0L)
    outputModel.update(0, "myPrompt: \n\n\n")
    blocksModel.updateCommandStartOffset(outputModel.startOffset + 10L)
    outputModel.update(0, "myPrompt: myCommand\n\n\n")
    blocksModel.updateOutputStartOffset(outputModel.startOffset + 20L)
    outputModel.update(1, "someOutput\n\n")

    assertEquals(1, blocksModel.blocks.size)
    val block = blocksModel.activeBlock as TerminalCommandBlock
    assertEquals("myCommand\n", outputModel.getTextAsString(block.commandStartOffset!!, block.outputStartOffset!!))
    assertEquals("myCommand", block.getTypedCommandText(outputModel))
    assertEquals("someOutput\n\n", outputModel.getTextAsString(block.outputStartOffset!!, block.endOffset))
    assertEquals("someOutput", block.getOutputText(outputModel))
    assertEquals(true, block.wasExecuted)
    assertEquals("myPrompt: myCommand\nsomeOutput\n\n", outputModel.getTextAsString(block.startOffset, block.endOffset))
  }

  @Test
  fun `aborted command block contains valid command and no output`() = runBlocking(Dispatchers.EDT) {
    val outputModel = TerminalTestUtil.createOutputModel()
    val blocksModel = createBlocksModel(outputModel)

    outputModel.update(0, "\n\n\n")
    blocksModel.startNewBlock(TerminalOffset.ZERO)
    outputModel.update(0, "myPrompt: \n\n\n")
    blocksModel.updateCommandStartOffset(TerminalOffset.of(10))
    outputModel.update(0, "myPrompt: abortedCommand\n\n\n")
    blocksModel.startNewBlock(TerminalOffset.of(25))
    outputModel.update(1, "myPrompt: \n\n")
    blocksModel.updateCommandStartOffset(TerminalOffset.of(35))

    assertEquals(2, blocksModel.blocks.size)
    val firstBlock = blocksModel.blocks[0] as TerminalCommandBlock
    assertEquals("abortedCommand", firstBlock.getTypedCommandText(outputModel))
    assertEquals(null, firstBlock.getOutputText(outputModel))
    assertEquals(false, firstBlock.wasExecuted)
  }

  @Test
  fun `getOutputText of command with no output is empty`() = runBlocking(Dispatchers.EDT) {
    val outputModel = TerminalTestUtil.createOutputModel()
    val blocksModel = createBlocksModel(outputModel)

    outputModel.update(0, "\n\n\n")
    blocksModel.startNewBlock(TerminalOffset.ZERO)
    outputModel.update(0, "myPrompt: \n\n\n")
    blocksModel.updateCommandStartOffset(TerminalOffset.of(10))
    outputModel.update(0, "myPrompt: myCommand\n\n\n")
    blocksModel.updateOutputStartOffset(TerminalOffset.of(20))
    blocksModel.startNewBlock(TerminalOffset.of(20))
    outputModel.update(1, "myPrompt: \n\n")
    blocksModel.updateCommandStartOffset(TerminalOffset.of(30))

    assertEquals(2, blocksModel.blocks.size)
    val firstBlock = blocksModel.blocks[0] as TerminalCommandBlock
    assertEquals("myCommand", firstBlock.getTypedCommandText(outputModel))
    assertEquals("", firstBlock.getOutputText(outputModel))
    assertEquals(true, firstBlock.wasExecuted)
  }

  @Test
  fun `getOutputText of running command returns current output`() = runBlocking(Dispatchers.EDT) {
    val outputModel = TerminalTestUtil.createOutputModel()
    val blocksModel = createBlocksModel(outputModel)

    outputModel.update(0, "\n\n\n")
    blocksModel.startNewBlock(TerminalOffset.ZERO)
    outputModel.update(0, "myPrompt: \n\n\n")
    blocksModel.updateCommandStartOffset(TerminalOffset.of(10))
    outputModel.update(0, "myPrompt: myCommand\n\n\n")
    blocksModel.updateOutputStartOffset(TerminalOffset.of(20))
    outputModel.update(1, "someOutput...\n\n")

    assertEquals(1, blocksModel.blocks.size)
    val firstBlock = blocksModel.blocks[0] as TerminalCommandBlock
    assertEquals("myCommand", firstBlock.getTypedCommandText(outputModel))
    assertEquals("someOutput...", firstBlock.getOutputText(outputModel))
    assertEquals(true, firstBlock.wasExecuted)
  }

  @Test
  fun `getTypedCommandText returns null if command start is trimmed`() = runBlocking(Dispatchers.EDT) {
    val outputModel = TerminalTestUtil.createOutputModel(30)
    val blocksModel = createBlocksModel(outputModel)

    outputModel.update(0, "\n\n\n")
    blocksModel.startNewBlock(TerminalOffset.ZERO)
    outputModel.update(0, "myPrompt: \n\n\n")
    blocksModel.updateCommandStartOffset(TerminalOffset.of(10))
    outputModel.update(0, "myPrompt: myCommand myCommand\n\n\n")
    blocksModel.updateOutputStartOffset(TerminalOffset.of(30))
    outputModel.update(1, "someOutput\n\n")
    blocksModel.startNewBlock(TerminalOffset.of(41))
    outputModel.update(2, "myPrompt: \n")
    blocksModel.updateCommandStartOffset(TerminalOffset.of(51))

    assertEquals(2, blocksModel.blocks.size)
    val firstBlock = blocksModel.blocks[0] as TerminalCommandBlock
    assertEquals(null, firstBlock.getTypedCommandText(outputModel))
    assertEquals("someOutput", firstBlock.getOutputText(outputModel))
    assertEquals(true, firstBlock.wasExecuted)
  }

  @Test
  fun `getOutputText returns partial output if output start is trimmed`() = runBlocking(Dispatchers.EDT) {
    val outputModel = TerminalTestUtil.createOutputModel(25)
    val blocksModel = createBlocksModel(outputModel)

    outputModel.update(0, "\n\n")
    blocksModel.startNewBlock(TerminalOffset.ZERO)
    outputModel.update(0, "myPrompt: \n\n")
    blocksModel.updateCommandStartOffset(TerminalOffset.of(10))
    outputModel.update(0, "myPrompt: myCommand\n\n")
    blocksModel.updateOutputStartOffset(TerminalOffset.of(20))
    outputModel.update(1, "123456789-123456789\n")
    blocksModel.startNewBlock(TerminalOffset.of(40))
    outputModel.update(2, "myPrompt: ")
    blocksModel.updateCommandStartOffset(TerminalOffset.of(50))

    assertEquals(2, blocksModel.blocks.size)
    val firstBlock = blocksModel.blocks[0] as TerminalCommandBlock
    assertEquals(null, firstBlock.getTypedCommandText(outputModel))
    assertEquals("6789-123456789", firstBlock.getOutputText(outputModel))
    assertEquals(true, firstBlock.wasExecuted)
  }

  @Test
  fun `new block is created after next prompt start`() = runBlocking(Dispatchers.EDT) {
    val outputModel = TerminalTestUtil.createOutputModel()
    val blocksModel = createBlocksModel(outputModel)

    outputModel.update(0, "\n\n\n")
    blocksModel.startNewBlock(outputModel.startOffset + 0L)
    outputModel.update(0, "myPrompt: \n\n\n")
    blocksModel.updateCommandStartOffset(outputModel.startOffset + 10L)
    outputModel.update(0, "myPrompt: myCommand\n\n\n")
    blocksModel.updateOutputStartOffset(outputModel.startOffset + 20L)
    outputModel.update(1, "someOutput\n\n")
    blocksModel.startNewBlock(outputModel.startOffset + 31L)
    outputModel.update(2, "updatedPrompt: \n")
    blocksModel.updateCommandStartOffset(outputModel.startOffset + 46L)

    assertEquals(2, blocksModel.blocks.size)

    val firstBlock = blocksModel.blocks[0]
    assertEquals("myPrompt: myCommand\nsomeOutput\n", outputModel.getTextAsString(firstBlock.startOffset, firstBlock.endOffset))
    val secondBlock = blocksModel.blocks[1]
    assertEquals("updatedPrompt: \n", outputModel.getTextAsString(secondBlock.startOffset, secondBlock.endOffset))
  }

  @Test
  fun `initial block is left if there was some text`() = runBlocking(Dispatchers.EDT) {
    val outputModel = TerminalTestUtil.createOutputModel()
    val blocksModel = createBlocksModel(outputModel)

    outputModel.update(0, "\n\n\n")
    outputModel.update(0, "welcomeText\n\n\n")
    blocksModel.startNewBlock(outputModel.startOffset + 12L)
    outputModel.update(1, "myPrompt: \n\n")
    blocksModel.updateCommandStartOffset(outputModel.startOffset + 22L)

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
    val blocksModel = createBlocksModel(outputModel)

    // Prepare
    outputModel.update(0, "\n\n\n")
    blocksModel.startNewBlock(outputModel.startOffset + 0L)
    outputModel.update(0, "myPrompt: \n\n\n")
    blocksModel.updateCommandStartOffset(outputModel.startOffset + 10L)
    outputModel.update(0, "myPrompt: myCommand\n\n\n")
    blocksModel.updateOutputStartOffset(outputModel.startOffset + 20L)
    outputModel.update(1, "output123\n\n")
    blocksModel.startNewBlock(outputModel.startOffset + 30L)
    outputModel.update(2, "myPrompt: \n")
    blocksModel.updateCommandStartOffset(outputModel.startOffset + 40L)
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
    val blocksModel = createBlocksModel(outputModel)

    // Prepare
    outputModel.update(0, "\n\n\n")
    blocksModel.startNewBlock(outputModel.startOffset + 0L)
    outputModel.update(0, "myPrompt: \n\n\n")
    blocksModel.updateCommandStartOffset(outputModel.startOffset + 10L)
    outputModel.update(0, "myPrompt: myCommand\n\n\n")
    blocksModel.updateOutputStartOffset(outputModel.startOffset + 20L)
    outputModel.update(1, "output123\n\n")
    blocksModel.startNewBlock(outputModel.startOffset + 30L)
    outputModel.update(2, "myPrompt: \n")
    blocksModel.updateCommandStartOffset(outputModel.startOffset + 40L)

    assertEquals(2, blocksModel.blocks.size)

    // Test
    outputModel.update(2, "myPrompt: clear\n")
    blocksModel.updateOutputStartOffset(outputModel.startOffset + 46L)
    outputModel.update(0, "\n\n\n")  // full replace
    blocksModel.startNewBlock(outputModel.startOffset + 0L)
    outputModel.update(0, "myPrompt: \n\n\n")
    blocksModel.updateCommandStartOffset(outputModel.startOffset + 10L)

    assertEquals(1, blocksModel.blocks.size)
    val block = blocksModel.activeBlock
    assertEquals("myPrompt: \n\n\n", outputModel.getTextAsString(block.startOffset, block.endOffset))
  }

  @Test
  fun `single block is left after clear`() = runBlocking(Dispatchers.EDT) {
    val outputModel = TerminalTestUtil.createOutputModel()
    val blocksModel = createBlocksModel(outputModel)

    // Prepare
    outputModel.update(0, "\n\n\n")
    blocksModel.startNewBlock(outputModel.startOffset + 0L)
    outputModel.update(0, "myPrompt: \n\n\n")
    blocksModel.updateCommandStartOffset(outputModel.startOffset + 10L)
    outputModel.update(0, "myPrompt: myCommand\n\n\n")
    blocksModel.updateOutputStartOffset(outputModel.startOffset + 20L)
    outputModel.update(1, "output123\n\n")
    blocksModel.startNewBlock(outputModel.startOffset + 30L)
    outputModel.update(2, "myPrompt: \n")
    blocksModel.updateCommandStartOffset(outputModel.startOffset + 45L)
    outputModel.update(2, "myPrompt: clear\n")

    assertEquals(2, blocksModel.blocks.size)

    // Test
    outputModel.update(0, "")

    assertEquals(1, blocksModel.blocks.size)
    val block = blocksModel.activeBlock
    assertEquals("", outputModel.getTextAsString(block.startOffset, block.endOffset))
  }

  @Test
  fun `single block is left after clear (with trimming)`() = runBlocking(Dispatchers.EDT) {
    val outputModel = TerminalTestUtil.createOutputModel(100)
    val blocksModel = createBlocksModel(outputModel)

    // Prepare
    outputModel.update(0, "\n\n\n")
    blocksModel.startNewBlock(outputModel.startOffset + 0L)
    outputModel.update(0, "myPrompt: \n\n\n")
    blocksModel.updateCommandStartOffset(outputModel.startOffset + 10L)
    outputModel.update(0, "myPrompt: myCommand\n\n\n")
    blocksModel.updateOutputStartOffset(outputModel.startOffset + 20L)
    outputModel.update(1, "a".repeat(100) + "\n\n")
    outputModel.update(2, "output123\n")
    blocksModel.startNewBlock(outputModel.startOffset + 100L)
    outputModel.update(3, "myPrompt: ")
    blocksModel.updateCommandStartOffset(outputModel.startOffset + 100L)
    outputModel.update(3, "myPrompt: clear")

    assertEquals(2, blocksModel.blocks.size)

    // Test
    outputModel.update(0, "")

    assertEquals(1, blocksModel.blocks.size)
    val block = blocksModel.activeBlock
    assertEquals("", outputModel.getTextAsString(block.startOffset, block.endOffset))
  }

  @Test
  fun `block positions stay the same after output start trimmed`() = runBlocking(Dispatchers.EDT) {
    val outputModel = TerminalTestUtil.createOutputModel(maxLength = 30)
    val blocksModel = createBlocksModel(outputModel)

    outputModel.update(0, "\n\n\n")
    blocksModel.startNewBlock(TerminalOffset.ZERO)
    outputModel.update(0, "myPrompt: \n\n\n")
    blocksModel.updateCommandStartOffset(TerminalOffset.of(10))
    outputModel.update(0, "myPrompt: myCommand\n\n\n")
    blocksModel.updateOutputStartOffset(TerminalOffset.of(20))
    outputModel.update(1, "output123456\n\n")  // 4 chars from the start should be trimmed
    blocksModel.startNewBlock(TerminalOffset.of(33))
    outputModel.update(2, "myPrompt: \n")      // 10 chars from the start should be trimmed
    blocksModel.updateCommandStartOffset(TerminalOffset.of(43))

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
    val blocksModel = createBlocksModel(outputModel)

    outputModel.update(0, "\n\n\n")
    outputModel.update(0, "welcome12\n\n\n")
    blocksModel.startNewBlock(TerminalOffset.of(10))
    outputModel.update(1, "myPrompt: \n\n")
    blocksModel.updateCommandStartOffset(TerminalOffset.of(20))
    outputModel.update(1, "myPrompt: myCommand\n\n")  // 1 char from the start should be trimmed
    blocksModel.updateOutputStartOffset(TerminalOffset.of(30))
    outputModel.update(2, "output123456\n")           // 12 chars from the start should be trimmed (and first block removed)
    blocksModel.startNewBlock(TerminalOffset.of(43))
    outputModel.update(3, "myPrompt: \n")             // 10 chars from the start should be trimmed
    blocksModel.updateCommandStartOffset(TerminalOffset.of(53))

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
    val blocksModel = createBlocksModel(outputModel)

    // Prepare
    outputModel.update(0, "\n\n\n")
    blocksModel.startNewBlock(outputModel.startOffset + 0L)
    outputModel.update(0, "myPrompt: \n\n\n")
    blocksModel.updateCommandStartOffset(outputModel.startOffset + 10L)
    outputModel.update(0, "myPrompt: myCommand\n\n\n")
    blocksModel.updateOutputStartOffset(outputModel.startOffset + 20L)
    outputModel.update(1, "someOutput\n\n")
    blocksModel.startNewBlock(outputModel.startOffset + 31L)
    outputModel.update(2, "updatedPrompt: \n")
    blocksModel.updateCommandStartOffset(outputModel.startOffset + 46L)

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
      workingDirectory = null,
      executedCommand = null,
      exitCode = null
    )
    assertEquals(expectedFirstBlock, state.blocks[0])

    val expectedSecondBlock = TerminalCommandBlockImpl(
      id = TerminalBlockIdImpl(2),
      startOffset = outputModel.startOffset + 31,
      commandStartOffset = outputModel.startOffset + 46,
      outputStartOffset = null,
      endOffset = outputModel.startOffset + 47,
      workingDirectory = null,
      executedCommand = null,
      exitCode = null
    )
    assertEquals(expectedSecondBlock, state.blocks[1])
  }

  @Test
  fun `check state is restored correctly`() = runBlocking(Dispatchers.EDT) {
    val outputModel = TerminalTestUtil.createOutputModel()
    val blocksModel = createBlocksModel(outputModel)

    val firstBlock = TerminalCommandBlockImpl(
      id = TerminalBlockIdImpl(1),
      startOffset = outputModel.startOffset + 0,
      commandStartOffset = outputModel.startOffset + 10,
      outputStartOffset = outputModel.startOffset + 20,
      endOffset = outputModel.startOffset + 31,
      workingDirectory = null,
      executedCommand = null,
      exitCode = null
    )
    val secondBlock = TerminalCommandBlockImpl(
      id = TerminalBlockIdImpl(2),
      startOffset = outputModel.startOffset + 31,
      commandStartOffset = outputModel.startOffset + 46,
      outputStartOffset = null,
      endOffset = outputModel.startOffset + 47,
      workingDirectory = null,
      executedCommand = null,
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
    val sourceBlocksModel = createBlocksModel(sourceOutputModel)

    // Prepare
    sourceOutputModel.update(0, "\n\n\n")
    sourceBlocksModel.startNewBlock(sourceOutputModel.startOffset + 0)
    sourceOutputModel.update(0, "myPrompt: \n\n\n")
    sourceBlocksModel.updateCommandStartOffset(sourceOutputModel.startOffset + 10)
    sourceOutputModel.update(0, "myPrompt: myCommand\n\n\n")
    sourceBlocksModel.updateOutputStartOffset(sourceOutputModel.startOffset + 20)
    sourceOutputModel.update(1, "someOutput\n\n")
    sourceBlocksModel.startNewBlock(sourceOutputModel.startOffset + 31)
    sourceOutputModel.update(2, "updatedPrompt: \n")
    sourceBlocksModel.updateCommandStartOffset(sourceOutputModel.startOffset + 46)

    // Test
    val state = sourceBlocksModel.dumpState()
    val newOutputModel = TerminalTestUtil.createOutputModel()
    val newBlocksModel = createBlocksModel(newOutputModel)
    newBlocksModel.restoreFromState(state)

    assertEquals(3, state.blockIdCounter)
    assertEquals(2, state.blocks.size)

    val expectedFirstBlock = TerminalCommandBlockImpl(
      id = TerminalBlockIdImpl(1),
      startOffset = newOutputModel.startOffset + 0,
      commandStartOffset = newOutputModel.startOffset + 10,
      outputStartOffset = newOutputModel.startOffset + 20,
      endOffset = newOutputModel.startOffset + 31,
      workingDirectory = null,
      executedCommand = null,
      exitCode = null
    )
    assertEquals(expectedFirstBlock, state.blocks[0])

    val expectedSecondBlock = TerminalCommandBlockImpl(
      id = TerminalBlockIdImpl(2),
      startOffset = newOutputModel.startOffset + 31,
      commandStartOffset = newOutputModel.startOffset + 46,
      outputStartOffset = null,
      endOffset = newOutputModel.startOffset + 47,
      workingDirectory = null,
      executedCommand = null,
      exitCode = null
    )
    assertEquals(expectedSecondBlock, state.blocks[1])
  }

  private fun createBlocksModel(outputModel: TerminalOutputModel): TerminalBlocksModelImpl {
    val sessionModel = TerminalSessionModelImpl()
    return TerminalBlocksModelImpl(outputModel, sessionModel, testRootDisposable)
  }

  private fun MutableTerminalOutputModel.getTextAsString(startOffset: TerminalOffset, endOffset: TerminalOffset): String {
    return getText(startOffset, endOffset).toString()
  }

  private fun TerminalBlocksModelImpl.updateCommandStartOffset(offset: TerminalOffset) {
    updateActiveCommandBlock { block ->
      block.copy(commandStartOffset = offset)
    }
  }

  private fun TerminalBlocksModelImpl.updateOutputStartOffset(offset: TerminalOffset) {
    updateActiveCommandBlock { block ->
      block.copy(outputStartOffset = offset)
    }
  }
}