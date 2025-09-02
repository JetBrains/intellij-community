// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal

import com.intellij.openapi.util.SystemInfo
import com.intellij.terminal.ui.TerminalWidget
import com.intellij.util.containers.CollectionFactory
import com.jediterm.core.util.TermSize
import org.jetbrains.plugins.terminal.util.ShellIntegration
import java.nio.file.Path

class ShellStartupOptions private constructor(builder: Builder) {
  val workingDirectory: String? = builder.workingDirectory
  val shellCommand: List<String>? = builder.shellCommand
  val commandHistoryFileProvider: (() -> Path?)? = builder.commandHistoryFileProvider
  val initialTermSize: TermSize? = builder.initialTermSize
  val widget: TerminalWidget? = builder.widget
  val shellIntegration: ShellIntegration? = builder.shellIntegration
  val envVariables: Map<String, String> = builder.envVariables
  internal val startupMoment: TerminalStartupMoment? = builder.startupMoment

  fun builder(): Builder {
    return Builder(workingDirectory, shellCommand, commandHistoryFileProvider, initialTermSize,
                   widget, shellIntegration, envVariables, startupMoment)
  }

  override fun toString(): String {
    return "workingDirectory=$workingDirectory" +
           ", shellCommand=$shellCommand" +
           ", commandHistoryFileProvider=${commandHistoryFileProvider != null}" +
           ", initialTermSize=[$initialTermSize]" +
           ", shellIntegration=$shellIntegration" +
           ", envVariables=$envVariables" +
           ", widget=${widget != null}"
  }

  class Builder internal constructor(var workingDirectory: String?,
                                     var shellCommand: List<String>?,
                                     var commandHistoryFileProvider: (() -> Path?)?,
                                     var initialTermSize: TermSize?,
                                     var widget: TerminalWidget?,
                                     var shellIntegration: ShellIntegration? = null,
                                     var envVariables: Map<String, String> = createEnvVariablesMap(),
                                     internal var startupMoment: TerminalStartupMoment? = null) {

    constructor() : this(null, null, null, null, null)

    fun workingDirectory(workingDirectory: String?) = also { this.workingDirectory = workingDirectory }
    fun shellCommand(shellCommand: List<String>?) = also { this.shellCommand = shellCommand }
    fun envVariables(envs: Map<String, String>) = also { this.envVariables = envs }
    fun commandHistoryFileProvider(commandHistoryFileProvider: (() -> Path?)?) = also { this.commandHistoryFileProvider = commandHistoryFileProvider }
    fun initialTermSize(initialTermSize: TermSize?) = also { this.initialTermSize = initialTermSize }
    fun widget(widget: TerminalWidget?) = also { this.widget = widget }
    fun shellIntegration(shellIntegration: ShellIntegration?) = also { this.shellIntegration = shellIntegration }
    @JvmName("startupMoment")
    internal fun startupMoment(startupMoment: TerminalStartupMoment?) = also { this.startupMoment = startupMoment }

    fun modify(modifier: (Builder) -> Unit): Builder = also {
      modifier(this)
    }

    fun build() = ShellStartupOptions(this)

    override fun toString(): String = build().toString()
  }
}

@JvmOverloads
fun shellStartupOptions(workingDirectory: String?, modifier: ((ShellStartupOptions.Builder) -> Unit)? = null): ShellStartupOptions {
  return ShellStartupOptions.Builder().workingDirectory(workingDirectory).modify(modifier ?: {}).build()
}

@JvmOverloads
fun createEnvVariablesMap(content: Map<String, String> = emptyMap()): MutableMap<String, String> {
  return if (SystemInfo.isWindows) CollectionFactory.createCaseInsensitiveStringMap(content) else HashMap(content)
}