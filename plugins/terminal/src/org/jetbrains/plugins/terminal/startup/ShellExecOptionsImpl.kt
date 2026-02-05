// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.startup

import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.path.EelPath
import java.util.Collections

internal class ShellExecOptionsImpl(
  override val execCommand: ShellExecCommand,
  override val workingDirectory: EelPath,
  envs: Map<String, String>,
) : ShellExecOptions {

  override val eelDescriptor: EelDescriptor
    get() = workingDirectory.descriptor

  override val envs: Map<String, String> = Collections.unmodifiableMap(envs)
}
