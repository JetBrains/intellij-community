// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.completion.spec.impl

import com.intellij.terminal.completion.spec.ShellFileInfo
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
data class ShellFileInfoImpl(
  override val name: String,
  override val type: ShellFileInfo.Type,
) : ShellFileInfo