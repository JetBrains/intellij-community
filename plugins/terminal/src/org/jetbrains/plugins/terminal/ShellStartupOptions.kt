// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal

import com.intellij.terminal.ui.TerminalWidget
import com.jediterm.core.util.TermSize
import java.nio.file.Path

class ShellStartupOptions private constructor(builder: Builder) {

  val workingDirectory: String? = builder.workingDirectory
  val shellCommand: List<String>? = builder.shellCommand
  val commandHistoryFileProvider: (() -> Path?)? = builder.commandHistoryFileProvider
  val initialTermSize: TermSize? = builder.initialTermSize
  val widget: TerminalWidget? = builder.widget

  fun builder(): Builder {
    return Builder(workingDirectory, shellCommand, commandHistoryFileProvider, initialTermSize, widget)
  }

  override fun toString(): String {
    return "workingDirectory=$workingDirectory, shellCommand=$shellCommand" +
           ", commandHistoryFileProvider=${commandHistoryFileProvider != null}, initialTermSize=[$initialTermSize]" +
           ", widget=${widget != null}"
  }

  class Builder internal constructor(var workingDirectory: String?,
                                     var shellCommand: List<String>?,
                                     var commandHistoryFileProvider: (() -> Path?)?,
                                     var initialTermSize: TermSize?,
                                     var widget: TerminalWidget?) {

    constructor() : this(null, null, null, null, null)

    fun workingDirectory(workingDirectory: String?) = also { this.workingDirectory = workingDirectory }
    fun shellCommand(shellCommand: List<String>?) = also { this.shellCommand = shellCommand }
    fun commandHistoryFileProvider(commandHistoryFileProvider: (() -> Path?)?) = also { this.commandHistoryFileProvider = commandHistoryFileProvider }
    fun initialTermSize(initialTermSize: TermSize?) = also { this.initialTermSize = initialTermSize }
    fun widget(widget: TerminalWidget?) = also { this.widget = widget }

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
