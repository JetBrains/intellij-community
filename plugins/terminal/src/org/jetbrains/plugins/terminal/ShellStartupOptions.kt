// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal

import com.intellij.openapi.util.SystemInfo
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.annotations.MultiRoutingFileSystemPath
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.provider.asNioPath
import com.intellij.terminal.ui.TerminalWidget
import com.intellij.util.containers.CollectionFactory
import com.jediterm.core.util.TermSize
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.runner.InitialShellCommand
import org.jetbrains.plugins.terminal.startup.ShellExecCommandImpl
import org.jetbrains.plugins.terminal.startup.ShellExecOptions
import org.jetbrains.plugins.terminal.startup.ShellExecOptionsImpl
import org.jetbrains.plugins.terminal.startup.TerminalProcessType
import org.jetbrains.plugins.terminal.util.ShellIntegration
import java.nio.file.Path
import kotlin.io.path.pathString

class ShellStartupOptions private constructor(builder: Builder) {
  val workingDirectory: @MultiRoutingFileSystemPath String? = builder.workingDirectory
  private val finalWorkingDirectoryEelPath: EelPath? = builder.getFinalWorkingDirectoryEelPath()
  val shellCommand: List<String>? = builder.shellCommand
  @ApiStatus.Internal
  val initialShellCommand: InitialShellCommand? = builder.initialShellCommand
  val processType: TerminalProcessType = builder.processType
  val commandHistoryFileProvider: (() -> Path?)? = builder.commandHistoryFileProvider
  val initialTermSize: TermSize? = builder.initialTermSize
  val widget: TerminalWidget? = builder.widget
  val shellIntegration: ShellIntegration? = builder.shellIntegration
  val envVariables: Map<String, String> = builder.envVariables
  internal val startupMoment: TerminalStartupMoment? = builder.startupMoment

  @get:ApiStatus.Internal
  val eelDescriptorNotNull: EelDescriptor
    get() = workingDirectoryEelPathNotNull.descriptor

  @get:ApiStatus.Internal
  val workingDirectoryEelPathNotNull: EelPath
    get() = checkNotNull(finalWorkingDirectoryEelPath) {
      "Working directory has not been finalized by `setFinalWorkingDirectoryEelPath`."
    }

  fun builder(): Builder {
    return Builder(
      workingDirectory,
      finalWorkingDirectoryEelPath,
      shellCommand,
      initialShellCommand,
      processType,
      commandHistoryFileProvider,
      initialTermSize,
      widget,
      shellIntegration,
      envVariables,
      startupMoment
    )
  }

  override fun toString(): String {
    return "workingDirectory=$workingDirectory" +
           ", shellCommand=$shellCommand" +
           ", commandHistoryFileProvider=${commandHistoryFileProvider != null}" +
           ", initialTermSize=[$initialTermSize]" +
           ", shellIntegration=$shellIntegration" +
           ", envVariables=$envVariables" +
           ", processType=$processType" +
           ", widget=${widget != null}"
  }

  @ApiStatus.Internal
  @Throws(IllegalArgumentException::class)
  fun toExecOptions(): ShellExecOptions {
    return ShellExecOptionsImpl(
      ShellExecCommandImpl(checkNotNull(shellCommand) { "Shell command is null" }),
      workingDirectoryEelPathNotNull,
      envVariables
    )
  }

  class Builder internal constructor(
    private var workingDirectoryField: @MultiRoutingFileSystemPath String?,
    private var finalWorkingDirectoryEelPath: EelPath?,
    var shellCommand: List<String>?,
    var initialShellCommand: InitialShellCommand?,
    var processType: TerminalProcessType = TerminalProcessType.SHELL,
    var commandHistoryFileProvider: (() -> Path?)?,
    var initialTermSize: TermSize?,
    var widget: TerminalWidget?,
    var shellIntegration: ShellIntegration? = null,
    var envVariables: Map<String, String> = createEnvVariablesMap(),
    internal var startupMoment: TerminalStartupMoment? = null,
  ) {

    var workingDirectory: @MultiRoutingFileSystemPath String?
      get() = workingDirectoryField
      set(value) {
        if (finalWorkingDirectoryEelPath != null) {
          throw IllegalStateException("Cannot set workingDirectory: working directory has been finalized")
        }
        workingDirectoryField = value
      }

    internal fun getFinalWorkingDirectoryEelPath(): EelPath? = finalWorkingDirectoryEelPath

    fun setFinalWorkingDirectoryEelPath(finalWorkingDirectoryEelPath: EelPath): Builder = also {
      if (this.finalWorkingDirectoryEelPath != null) {
        throw IllegalStateException("Working directory has already been finalized and cannot be changed")
      }
      this.workingDirectory = finalWorkingDirectoryEelPath.asNioPath().pathString
      this.finalWorkingDirectoryEelPath = finalWorkingDirectoryEelPath
    }

    constructor() : this(null, null, null, null, TerminalProcessType.SHELL, null, null, null)

    fun workingDirectory(workingDirectory: @MultiRoutingFileSystemPath String?) = also { this.workingDirectory = workingDirectory }
    fun shellCommand(shellCommand: List<String>?) = also { this.shellCommand = shellCommand }
    fun envVariables(envs: Map<String, String>) = also { this.envVariables = envs }
    fun processType(processType: TerminalProcessType) = also { this.processType = processType }
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
fun shellStartupOptions(workingDirectory: @MultiRoutingFileSystemPath String?, modifier: ((ShellStartupOptions.Builder) -> Unit)? = null): ShellStartupOptions {
  return ShellStartupOptions.Builder().workingDirectory(workingDirectory).modify(modifier ?: {}).build()
}

@JvmOverloads
fun createEnvVariablesMap(content: Map<String, String> = emptyMap()): MutableMap<String, String> {
  return if (SystemInfo.isWindows) CollectionFactory.createCaseInsensitiveStringMap(content) else HashMap(content)
}