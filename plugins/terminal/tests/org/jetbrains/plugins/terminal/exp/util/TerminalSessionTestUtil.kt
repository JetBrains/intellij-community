// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp.util

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jediterm.core.util.TermSize
import org.jetbrains.plugins.terminal.LocalTerminalDirectRunner
import org.jetbrains.plugins.terminal.ShellStartupOptions
import org.jetbrains.plugins.terminal.exp.ShellCommandListener
import org.jetbrains.plugins.terminal.exp.TerminalModel
import org.jetbrains.plugins.terminal.exp.TerminalSession
import org.jetbrains.plugins.terminal.exp.ui.BlockTerminalColorPalette
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

object TerminalSessionTestUtil {
  fun startTerminalSession(project: Project,
                           shellPath: String,
                           disposable: Disposable,
                           size: TermSize = TermSize(200, 20)): TerminalSession {
    Registry.get(LocalTerminalDirectRunner.BLOCK_TERMINAL_REGISTRY).setValue(true, disposable)
    val runner = LocalTerminalDirectRunner.createTerminalRunner(project)
    val baseOptions = ShellStartupOptions.Builder().shellCommand(listOf(shellPath, "-i")).initialTermSize(size).build()
    val configuredOptions = runner.configureStartupOptions(baseOptions)
    val process = runner.createProcess(configuredOptions)
    val ttyConnector = runner.createTtyConnector(process)

    val colorPalette = BlockTerminalColorPalette(EditorColorsManager.getInstance().globalScheme)
    val session = TerminalSession(runner.settingsProvider, colorPalette, configuredOptions.shellIntegration)
    val model: TerminalModel = session.model

    val promptShownFuture = CompletableFuture<Boolean>()
    val listenersDisposable = Disposer.newDisposable()
    session.addCommandListener(object : ShellCommandListener {
      override fun promptShown() {
        promptShownFuture.complete(true)
      }
    }, listenersDisposable)

    session.start(ttyConnector)

    try {
      promptShownFuture.get(5000, TimeUnit.MILLISECONDS)
    }
    catch (ex: TimeoutException) {
      BasePlatformTestCase.fail(
        "Session failed to initialize, size: ${model.height}x${model.width}, text buffer:\n${model.withContentLock { model.getAllText() }}")
    }
    finally {
      Disposer.dispose(listenersDisposable)
    }
    // Remove all welcome messages
    model.withContentLock { model.clearAllAndMoveCursorToTopLeftCorner(session.controller) }

    return session
  }
}