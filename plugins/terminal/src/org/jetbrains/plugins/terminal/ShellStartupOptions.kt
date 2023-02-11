// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal

import com.jediterm.core.util.TermSize

class ShellStartupOptions private constructor(builder: Builder) {

  val workingDirectory: String? = builder.workingDirectory
  val shellCommand: List<String>? = builder.shellCommand
  val initialTermSize: TermSize? = builder.initialTermSize

  fun builder(): Builder {
    return Builder(workingDirectory, shellCommand, initialTermSize)
  }

  override fun toString(): String {
    return "workingDirectory=$workingDirectory, shellCommand=$shellCommand, initialTermSize=$initialTermSize"
  }

  class Builder(var workingDirectory: String? = null,
                var shellCommand: List<String>? = null,
                var initialTermSize: TermSize? = null) {

    fun workingDirectory(workingDirectory: String?) = also { this.workingDirectory = workingDirectory }
    fun initialTermSize(initialTermSize: TermSize?) = also { this.initialTermSize = initialTermSize }

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
