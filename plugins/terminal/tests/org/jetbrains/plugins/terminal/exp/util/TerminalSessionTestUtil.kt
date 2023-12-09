// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp.util

import com.intellij.execution.Platform
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.execution.ParametersListUtil
import com.jediterm.core.util.TermSize
import com.jediterm.terminal.RequestOrigin
import org.jetbrains.plugins.terminal.LocalBlockTerminalRunner
import org.jetbrains.plugins.terminal.ShellStartupOptions
import org.jetbrains.plugins.terminal.exp.ShellCommandListener
import org.jetbrains.plugins.terminal.exp.TerminalModel
import org.jetbrains.plugins.terminal.exp.BlockTerminalSession
import org.jetbrains.plugins.terminal.exp.ui.BlockTerminalColorPalette
import org.junit.Assume
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

object TerminalSessionTestUtil {
  fun startBlockTerminalSession(project: Project,
                                shellPath: String,
                                parentDisposable: Disposable,
                                initialTermSize: TermSize = TermSize(200, 20)): BlockTerminalSession {
    Registry.get(LocalBlockTerminalRunner.BLOCK_TERMINAL_REGISTRY).setValue(true, parentDisposable)
    Registry.get(LocalBlockTerminalRunner.BLOCK_TERMINAL_FISH_REGISTRY).setValue(true, parentDisposable)
    Registry.get(LocalBlockTerminalRunner.BLOCK_TERMINAL_POWERSHELL_REGISTRY).setValue(true, parentDisposable)
    val runner = LocalBlockTerminalRunner(project)
    val baseOptions = ShellStartupOptions.Builder().shellCommand(listOf(shellPath)).initialTermSize(initialTermSize).build()
    val configuredOptions = runner.configureStartupOptions(baseOptions)
    assumeBlockShellIntegration(configuredOptions)
    val process = runner.createProcess(configuredOptions)
    val ttyConnector = runner.createTtyConnector(process)

    val colorPalette = BlockTerminalColorPalette(EditorColorsManager.getInstance().globalScheme)
    val session = BlockTerminalSession(runner.settingsProvider, colorPalette, configuredOptions.shellIntegration!!)
    Disposer.register(parentDisposable, session)
    session.controller.resize(initialTermSize, RequestOrigin.User)
    val model: TerminalModel = session.model

    val initializedFuture = CompletableFuture<Boolean>()
    val listenersDisposable = Disposer.newDisposable()
    session.addCommandListener(object : ShellCommandListener {
      override fun initialized(currentDirectory: String?) {
        initializedFuture.complete(true)
      }
    }, listenersDisposable)

    session.start(ttyConnector)

    try {
      initializedFuture.get(5000, TimeUnit.MILLISECONDS)
    }
    catch (ex: TimeoutException) {
      BasePlatformTestCase.fail(
        "Session failed to initialize, size: ${model.height}x${model.width}, text buffer:\n${model.withContentLock { model.getAllText() }}")
    }
    finally {
      Disposer.dispose(listenersDisposable)
    }

    return session
  }

  private fun assumeBlockShellIntegration(options: ShellStartupOptions) {
    Assume.assumeTrue("Block shell integration is expected", options.shellIntegration?.withCommandBlocks == true)
  }

  fun getJavaShellCommand(mainClass: Class<*>, vararg args: String): String {
    val command = getJavaCommand(mainClass, args.toList())
    return ParametersListUtil.join(command)
  }

  private fun getJavaCommand(mainClass: Class<*>, args: List<String>): List<String> {
    return listOf(getJavaExecutablePath().toString(),
                  "-cp",
                  getJarPathForClasses(listOf(mainClass, KotlinVersion::class.java /* kotlin-stdlib.jar */)),
                  mainClass.name) +
           args
  }

  private fun getJavaExecutablePath(): Path {
    return Path.of(System.getProperty("java.home"), "bin", if (Platform.current() == Platform.WINDOWS) "java.exe" else "java")
  }

  private fun getJarPathForClasses(classes: List<Class<*>>): String {
    return classes.joinToString(Platform.current().pathSeparator.toString()) {
      checkNotNull(PathManager.getJarPathForClass(it)) { "Cannot find jar/directory for $it" }
    }
  }
}