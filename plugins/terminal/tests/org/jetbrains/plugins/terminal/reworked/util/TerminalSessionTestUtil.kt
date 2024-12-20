// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.reworked.util

import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.EnvironmentUtil
import com.intellij.util.asDisposable
import com.jediterm.core.util.TermSize
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.plugins.terminal.LocalBlockTerminalRunner
import org.jetbrains.plugins.terminal.ShellStartupOptions
import org.jetbrains.plugins.terminal.block.reworked.TerminalOutputModel
import org.jetbrains.plugins.terminal.block.reworked.TerminalOutputModelImpl
import org.jetbrains.plugins.terminal.block.reworked.session.TerminalSession
import org.jetbrains.plugins.terminal.block.reworked.session.startTerminalSession
import org.jetbrains.plugins.terminal.block.session.StyleRange
import java.nio.file.Files
import java.nio.file.Path

internal object TerminalSessionTestUtil {
  fun startTestTerminalSession(
    shellPath: String,
    project: Project,
    coroutineScope: CoroutineScope,
    size: TermSize = TermSize(80, 24),
  ): TerminalSession {
    Registry.get(LocalBlockTerminalRunner.REWORKED_BLOCK_TERMINAL_REGISTRY).setValue(true, coroutineScope.asDisposable())

    val runner = LocalBlockTerminalRunner(project)

    val baseOptions = ShellStartupOptions.Builder()
      .shellCommand(listOf(shellPath))
      .initialTermSize(size)
      .envVariables(mapOf(EnvironmentUtil.DISABLE_OMZ_AUTO_UPDATE to "true", "HISTFILE" to "/dev/null"))
      .build()
    val configuredOptions = runner.configureStartupOptions(baseOptions)

    val process = runner.createProcess(configuredOptions)
    val connector = runner.createTtyConnector(process)
    return startTerminalSession(connector, size, runner.settingsProvider, coroutineScope)
  }

  fun getShellPaths(): List<Path> {
    return listOf(
      "/bin/zsh",
      "/urs/bin/zsh",
      "/urs/local/bin/zsh",
      "/opt/homebrew/bin/zsh",
    ).mapNotNull {
      val path = Path.of(it)
      if (Files.isRegularFile(path)) path else PathEnvironmentVariableUtil.findInPath(it)?.toPath()
    }
  }

  fun createOutputModel(project: Project, parentDisposable: Disposable, maxLength: Int = 0): TerminalOutputModelImpl {
    val document = EditorFactory.getInstance().createDocument("")
    val editor = EditorFactory.getInstance().createEditor(document, project) as EditorEx
    Disposer.register(parentDisposable) {
      EditorFactory.getInstance().releaseEditor(editor)
    }

    return TerminalOutputModelImpl(editor, maxLength)
  }

  suspend fun TerminalOutputModel.update(absoluteLineIndex: Int, text: String, styles: List<StyleRange> = emptyList()) {
    writeAction {
      updateContent(absoluteLineIndex, text, styles)
    }
  }
}