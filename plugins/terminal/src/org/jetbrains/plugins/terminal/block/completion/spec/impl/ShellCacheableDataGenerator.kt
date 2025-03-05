// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.completion.spec.impl

import com.intellij.terminal.completion.spec.ShellRuntimeContext
import com.intellij.terminal.completion.spec.ShellRuntimeDataGenerator

internal interface ShellCacheableDataGenerator<T : Any> : ShellRuntimeDataGenerator<T> {
  fun getCacheKey(context: ShellRuntimeContext): String?
}
