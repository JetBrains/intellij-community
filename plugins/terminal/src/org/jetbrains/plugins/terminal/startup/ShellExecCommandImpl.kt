// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.startup

import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.util.PathUtil
import com.intellij.util.execution.ParametersListUtil
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.session.ShellName

@ApiStatus.Internal
class ShellExecCommandImpl(command: List<String>) : ShellExecCommand {

  init {
    require(command.isNotEmpty()) { "Shell command cannot be empty" }
  }

  override val command: ImmutableList<String> = command.toImmutableList()

  override val shellName: ShellName = ShellName.of(
    FileUtilRt.getNameWithoutExtension(PathUtil.getFileName(command.first()))
  )

  override fun toString(): String = ParametersListUtil.join(command)

  override fun hashCode(): Int = command.hashCode()

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false
    other as ShellExecCommandImpl
    return command == other.command
  }
}
