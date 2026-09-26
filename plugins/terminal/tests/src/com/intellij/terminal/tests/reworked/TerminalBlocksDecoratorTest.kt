package com.intellij.terminal.tests.reworked

import com.intellij.openapi.application.EDT
import com.intellij.openapi.util.Disposer
import com.intellij.platform.util.coroutines.childScope
import com.intellij.terminal.frontend.view.impl.TerminalBlocksDecorator
import com.intellij.terminal.frontend.view.impl.TerminalEditorFactory
import com.intellij.terminal.frontend.view.impl.TerminalOutputScrollingModelImpl
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.asDisposable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.terminal.JBTerminalSystemSettingsProvider
import org.jetbrains.plugins.terminal.block.BlockTerminalOptions
import org.jetbrains.plugins.terminal.block.reworked.TerminalSessionModelImpl
import org.jetbrains.plugins.terminal.view.impl.MutableTerminalOutputModelImpl
import org.jetbrains.plugins.terminal.view.shellIntegration.impl.TerminalBlocksModelImpl
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
internal class TerminalBlocksDecoratorTest : BasePlatformTestCase() {
  override fun runInDispatchThread(): Boolean = false

  @Test
  fun `decorations are created for initial blocks after decorator init (separators enabled)`() = timeoutRunBlocking(context = Dispatchers.EDT) {
    doWithChildScopeAndCancel {
      setSeparatorsStateForTest(true)
      val decorator = createDecoratorWithInitialState(scope = this)
      // Decoration is created for a single initial block
      assertThat(decorator.decorations).hasSize(1)
    }
  }

  @Test
  fun `decorations are not created for initial blocks after decorator init (separators disabled)`() = timeoutRunBlocking(context = Dispatchers.EDT) {
    doWithChildScopeAndCancel {
      setSeparatorsStateForTest(false)
      val decorator = createDecoratorWithInitialState(scope = this)
      // No decorations are created
      assertThat(decorator.decorations).isEmpty()
    }
  }

  private fun createDecoratorWithInitialState(scope: CoroutineScope): TerminalBlocksDecorator {
    val editor = TerminalEditorFactory.createOutputEditor(project, JBTerminalSystemSettingsProvider(), scope)
    val outputModel = MutableTerminalOutputModelImpl(editor.document, maxOutputLength = 0)
    val sessionModel = TerminalSessionModelImpl()
    val blocksModel = TerminalBlocksModelImpl(outputModel, sessionModel, scope.asDisposable())
    val scrollingModel = TerminalOutputScrollingModelImpl(editor, outputModel, sessionModel, scope)
    return TerminalBlocksDecorator(editor, outputModel, blocksModel, scrollingModel, scope)
  }

  private suspend fun CoroutineScope.doWithChildScopeAndCancel(action: suspend CoroutineScope.() -> Unit) {
    val childScope = childScope("test")
    try {
      childScope.action()
    }
    finally {
      childScope.cancel()
    }
  }

  private fun setSeparatorsStateForTest(enabled: Boolean) {
    val options = BlockTerminalOptions.getInstance()
    val prevValue = options.showSeparatorsBetweenBlocks
    options.showSeparatorsBetweenBlocks = enabled
    Disposer.register(testRootDisposable) {
      options.showSeparatorsBetweenBlocks = prevValue
    }
  }
}